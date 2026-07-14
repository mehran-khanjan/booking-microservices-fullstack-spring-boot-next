package com.example.bookingservice.servcie;

import com.example.bookingservice.config.LockingStrategyConfig;
import com.example.bookingservice.constant.ErrorCodes;
import com.example.bookingservice.dto.controller.BookingResponse;
import com.example.bookingservice.dto.controller.CreateBookingRequest;
import com.example.bookingservice.entity.*;
import com.example.bookingservice.enums.CabinClass;
import com.example.bookingservice.exception.BusinessException;
import com.example.bookingservice.mapper.BookingMapper;
import com.example.bookingservice.repository.BookingRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for creating and managing flight bookings. Handles seat reservation, flight
 * denormalization, and transactional persistence. Implements both distributed and optimistic
 * locking strategies.
 */
@Service
@Slf4j
@AllArgsConstructor
public class BookingService {

  private final LockingStrategyConfig lockingConfig;
  private final BookingLockingService lockingService;
  private final BookingRepository bookingRepository;
  private final BookingMapper bookingMapper;
  private final FlightServiceClient flightServiceClient;

  private static final int BOOKING_HOLD_MINUTES = 15;
  private static final int GROUP_BOOKING_HOLD_HOURS = 24;
  private static final int MAX_BOOKING_REFERENCE_ATTEMPTS = 3;

  /**
   * Creates a new booking for the given user. Validates seat availability, reserves seats via
   * flight‑service, persists the booking, and applies the configured locking strategy.
   *
   * @param request the booking request containing flights, passengers, and services
   * @param userId the authenticated user identifier
   * @return the created booking as a response DTO
   * @throws BusinessException if validation fails, seats are unavailable, or persistence fails
   */
  @Transactional
  public BookingResponse createBooking(CreateBookingRequest request, String userId) {
    String correlationId = UUID.randomUUID().toString();

    log.info(
        "Creating booking for user: {}, flights: {}, passengers: {}",
        userId,
        request.getFlights().size(),
        request.getPassengers().size());

    validateBookingRequest(request);

    String lockingStrategy = lockingConfig.getStrategy();

    try {
      return switch (lockingStrategy.toUpperCase()) {
        case "OPTIMISTIC" -> createBookingWithOptimisticLock(request, userId, correlationId);
        case "DISTRIBUTED", "PESSIMISTIC" ->
            createBookingWithDistributedLock(request, userId, correlationId);
        default ->
            throw new BusinessException(
                "Invalid locking strategy: " + lockingStrategy, ErrorCodes.VALIDATION_ERROR);
      };
    } catch (Exception e) {
      log.error("Booking creation failed: {}", e.getMessage(), e);
      throw e;
    }
  }

  // -------------------- Locking strategies --------------------

  /**
   * Creates a booking using distributed locks (Redis) on each distinct flight. This ensures that
   * concurrent bookings on overlapping flights are serialized.
   */
  private BookingResponse createBookingWithDistributedLock(
      CreateBookingRequest request, String userId, String correlationId) {
    log.info("Using DISTRIBUTED locking strategy via BookingLockingService");

    List<String> lockKeys =
        request.getFlights().stream()
            .map(selection -> "flight:lock:" + selection.getFlightId())
            .distinct()
            .sorted()
            .collect(Collectors.toList());

    return lockingService.executeWithMultiLock(
        lockKeys,
        10,
        30,
        () -> {
          Map<Long, Flight> flightsById = fetchFlightsById(request);
          return processBookingCreation(request, userId, correlationId, flightsById);
        });
  }

  /**
   * Creates a booking using optimistic locking (retry on version conflicts). Relies on JPA's
   * {@code @Version} on the Booking entity.
   */
  private BookingResponse createBookingWithOptimisticLock(
      CreateBookingRequest request, String userId, String correlationId) {
    log.info("Using OPTIMISTIC locking strategy");

    int maxRetries = 3;
    int attempt = 0;

    while (attempt < maxRetries) {
      try {
        Map<Long, Flight> flightsById = fetchFlightsById(request);
        return processBookingCreation(request, userId, correlationId, flightsById);
      } catch (BusinessException e) {
        if (ErrorCodes.OPTIMISTIC_LOCK_FAILURE.equals(e.getErrorCode())) {
          attempt++;
          log.warn("Optimistic lock failure, attempt {}/{}", attempt, maxRetries);
          if (attempt >= maxRetries) {
            throw new BusinessException(
                "Failed to create booking after " + maxRetries + " attempts",
                ErrorCodes.OPTIMISTIC_LOCK_FAILURE);
          }
          sleepBackoff(attempt);
        } else {
          throw e;
        }
      }
    }
    throw new BusinessException("Failed to create booking", ErrorCodes.VALIDATION_ERROR);
  }

  // -------------------- Helper methods --------------------

  /**
   * Fetches flight details from flight‑service and returns them keyed by flight ID. BUG FIX (#1,
   * #2): ensures that the returned map preserves the correct association between a requested flight
   * ID and its data, regardless of the order of results.
   *
   * @param request the booking request containing the flight IDs
   * @return a map from flight ID to Flight entity
   * @throws BusinessException if any requested flight is missing
   */
  private Map<Long, Flight> fetchFlightsById(CreateBookingRequest request) {
    List<Long> requestedIds =
        request.getFlights().stream()
            .map(CreateBookingRequest.FlightSelection::getFlightId)
            .distinct()
            .collect(Collectors.toList());

    List<Flight> flights = flightServiceClient.getFlights(requestedIds);

    Map<Long, Flight> flightsById =
        flights.stream().collect(Collectors.toMap(Flight::getId, f -> f));

    List<Long> missingIds =
        requestedIds.stream()
            .filter(id -> !flightsById.containsKey(id))
            .collect(Collectors.toList());

    if (!missingIds.isEmpty()) {
      throw new BusinessException(
          "The following flights could not be found or are no longer available: " + missingIds,
          ErrorCodes.FLIGHT_NOT_FOUND);
    }

    return flightsById;
  }

  /**
   * Processes the actual booking creation: validates seat availability, reserves seats, builds the
   * booking entity, and persists it with compensation on failure.
   */
  private BookingResponse processBookingCreation(
      CreateBookingRequest request,
      String userId,
      String correlationId,
      Map<Long, Flight> flightsById) {

    validateSeatAvailability(request, flightsById);

    Booking booking = buildBooking(request, userId, flightsById);

    // Reserve seats via gRPC – with compensation if any fails
    List<Long> reservedFlightIds = new ArrayList<>();
    try {
      for (CreateBookingRequest.FlightSelection selection : request.getFlights()) {
        Flight flight = flightsById.get(selection.getFlightId());
        Map<String, Integer> cabinSeats =
            Map.of(selection.getCabinClass().toUpperCase(), request.getPassengers().size());
        boolean success =
            flightServiceClient.reserveSeats(
                flight.getId(),
                booking.getBookingReference(),
                request.getPassengers().size(),
                cabinSeats);
        if (!success) {
          throw new BusinessException(
              "Seat reservation failed for flight: " + flight.getId(),
              ErrorCodes.SEAT_RESERVATION_FAILED);
        }
        reservedFlightIds.add(flight.getId());
      }
    } catch (Exception e) {
      releaseSeatsForFlights(request, booking.getBookingReference(), reservedFlightIds);
      throw new BusinessException(
          "Booking failed, released reserved seats", ErrorCodes.SEAT_RESERVATION_FAILED, e);
    }

    Booking savedBooking = persistBookingWithCompensation(booking, request, reservedFlightIds);

    log.info(
        "Booking created successfully: {}, totalAmount={}",
        savedBooking.getBookingReference(),
        savedBooking.getTotalAmount());

    return bookingMapper.toResponse(savedBooking);
  }

  /**
   * Persists the booking and handles reference collisions or other persistence errors, releasing
   * reserved seats if persistence fails. BUG FIX (#3, #4): retries on {@link
   * DataIntegrityViolationException} for duplicate booking references, and always releases seats on
   * any failure.
   */
  private Booking persistBookingWithCompensation(
      Booking booking, CreateBookingRequest request, List<Long> reservedFlightIds) {

    for (int attempt = 1; attempt <= MAX_BOOKING_REFERENCE_ATTEMPTS; attempt++) {
      try {
        return bookingRepository.save(booking);
      } catch (DataIntegrityViolationException collision) {
        log.warn(
            "Booking reference collision on attempt {}/{} for reference {}, regenerating",
            attempt,
            MAX_BOOKING_REFERENCE_ATTEMPTS,
            booking.getBookingReference());
        if (attempt == MAX_BOOKING_REFERENCE_ATTEMPTS) {
          releaseSeatsForFlights(request, booking.getBookingReference(), reservedFlightIds);
          throw new BusinessException(
              "Failed to persist booking after "
                  + MAX_BOOKING_REFERENCE_ATTEMPTS
                  + " reference collisions",
              ErrorCodes.VALIDATION_ERROR,
              collision);
        }
        booking.setBookingReference(generateBookingReference());
      } catch (Exception e) {
        log.error(
            "Unexpected error persisting booking {}, releasing reserved seats",
            booking.getBookingReference(),
            e);
        releaseSeatsForFlights(request, booking.getBookingReference(), reservedFlightIds);
        throw new BusinessException(
            "Booking could not be saved; reserved seats have been released",
            ErrorCodes.SEAT_RESERVATION_FAILED,
            e);
      }
    }
    throw new IllegalStateException("Unreachable");
  }

  /** Releases seats for a list of flight IDs as compensation for a failed booking. */
  private void releaseSeatsForFlights(
      CreateBookingRequest request, String bookingRef, List<Long> flightIds) {
    for (Long flightId : flightIds) {
      CreateBookingRequest.FlightSelection selection =
          request.getFlights().stream()
              .filter(s -> s.getFlightId().equals(flightId))
              .findFirst()
              .orElse(null);
      if (selection != null) {
        Map<String, Integer> cabinSeats =
            Map.of(selection.getCabinClass().toUpperCase(), request.getPassengers().size());
        try {
          flightServiceClient.releaseSeats(
              flightId, bookingRef, request.getPassengers().size(), cabinSeats);
        } catch (Exception ex) {
          log.error("Failed to release seats for flight {} during compensation", flightId, ex);
        }
      }
    }
  }

  /**
   * Validates that the required number of seats is available on each flight for the requested cabin
   * class.
   */
  private void validateSeatAvailability(
      CreateBookingRequest request, Map<Long, Flight> flightsById) {
    for (CreateBookingRequest.FlightSelection selection : request.getFlights()) {
      Flight flight = flightsById.get(selection.getFlightId());

      if (flight.getStatus() != Flight.FlightStatus.SCHEDULED) {
        throw new BusinessException(
            "Flight " + flight.getFlightNumber() + " is not available",
            ErrorCodes.FLIGHT_NOT_AVAILABLE);
      }

      int requiredSeats = request.getPassengers().size();
      int available = getAvailableSeatsForCabinClass(flight, selection.getCabinClass());

      if (available < requiredSeats) {
        throw new BusinessException(
            "Insufficient seats on flight "
                + flight.getFlightNumber()
                + " in "
                + selection.getCabinClass()
                + " class",
            ErrorCodes.INSUFFICIENT_SEATS);
      }
    }
  }

  private int getAvailableSeatsForCabinClass(Flight flight, String cabinClass) {
    return switch (cabinClass.toUpperCase()) {
      case "ECONOMY" -> flight.getEconomyAvailable();
      case "PREMIUM_ECONOMY" -> flight.getPremiumEconomyAvailable();
      case "BUSINESS" -> flight.getBusinessAvailable();
      case "FIRST_CLASS" -> flight.getFirstClassAvailable();
      default -> flight.getAvailableSeats();
    };
  }

  /**
   * Builds the Booking entity from the request and fetched flight data. Denormalizes flight
   * details, calculates totals, and sets the hold expiry.
   */
  private Booking buildBooking(
      CreateBookingRequest request, String userId, Map<Long, Flight> flightsById) {
    Booking booking = new Booking();
    booking.setBookingReference(generateBookingReference());
    booking.setUserId(userId);
    booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
    booking.setContactEmail(request.getContactEmail());
    booking.setContactPhone(request.getContactPhone());
    booking.setCurrency(request.getCurrency() != null ? request.getCurrency() : "USD");

    if (request.getFlights().size() == 1) {
      booking.setBookingType(Booking.BookingType.SINGLE);
    } else if (request.getFlights().size() == 2 && isRoundTrip(request, flightsById)) {
      booking.setBookingType(Booking.BookingType.ROUND_TRIP);
    } else {
      booking.setBookingType(Booking.BookingType.MULTI_CITY);
    }

    int holdMinutes =
        request.getPassengers().size() >= 10 ? GROUP_BOOKING_HOLD_HOURS * 60 : BOOKING_HOLD_MINUTES;
    booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(holdMinutes));

    BigDecimal totalAmount = BigDecimal.ZERO;
    for (CreateBookingRequest.FlightSelection selection : request.getFlights()) {
      Flight flight = flightsById.get(selection.getFlightId());

      BookingFlight bf = new BookingFlight();
      bf.setFlightId(flight.getId());
      bf.setFlightNumber(flight.getFlightNumber());
      bf.setAirlineName(flight.getAirline().getName());
      bf.setOriginAirportCode(flight.getOriginAirport().getIataCode());
      bf.setDestinationAirportCode(flight.getDestinationAirport().getIataCode());
      bf.setDepartureDate(flight.getDepartureDate());
      bf.setDepartureTime(flight.getDepartureTime());
      bf.setArrivalDate(flight.getArrivalDate());
      bf.setArrivalTime(flight.getArrivalTime());
      bf.setSegmentOrder(selection.getSegmentOrder());
      bf.setCabinClass(CabinClass.valueOf(selection.getCabinClass().toUpperCase()));

      BigDecimal basePrice = getPriceForCabinClass(flight, selection.getCabinClass());
      BigDecimal taxes = basePrice.multiply(BigDecimal.valueOf(0.15));
      bf.setBasePrice(basePrice);
      bf.setTaxesFees(taxes);

      booking.addBookingFlight(bf);
      totalAmount = totalAmount.add(basePrice).add(taxes);
    }

    for (CreateBookingRequest.PassengerDetails passengerDetails : request.getPassengers()) {
      Passenger passenger = bookingMapper.toPassengerEntity(passengerDetails);
      booking.addPassenger(passenger);
    }

    if (request.getAdditionalServices() != null) {
      for (CreateBookingRequest.AdditionalServiceRequest sr : request.getAdditionalServices()) {
        AdditionalService service =
            AdditionalService.builder()
                .serviceType(AdditionalService.ServiceType.valueOf(sr.getServiceType()))
                .description(sr.getDescription())
                .price(getServicePrice(sr.getServiceType()))
                .currency(booking.getCurrency())
                .build();
        booking.addAdditionalService(service);
        totalAmount = totalAmount.add(service.getPrice());
      }
    }

    booking.setTotalAmount(totalAmount);
    return booking;
  }

  private boolean isRoundTrip(CreateBookingRequest request, Map<Long, Flight> flightsById) {
    if (request.getFlights().size() != 2) return false;
    Flight outbound = flightsById.get(request.getFlights().get(0).getFlightId());
    Flight inbound = flightsById.get(request.getFlights().get(1).getFlightId());
    return outbound
            .getOriginAirport()
            .getIataCode()
            .equals(inbound.getDestinationAirport().getIataCode())
        && outbound
            .getDestinationAirport()
            .getIataCode()
            .equals(inbound.getOriginAirport().getIataCode());
  }

  private BigDecimal getPriceForCabinClass(Flight flight, String cabinClass) {
    return switch (cabinClass.toUpperCase()) {
      case "ECONOMY" -> flight.getBasePriceEconomy();
      case "PREMIUM_ECONOMY" -> flight.getBasePricePremiumEconomy();
      case "BUSINESS" -> flight.getBasePriceBusiness();
      case "FIRST_CLASS" -> flight.getBasePriceFirstClass();
      default -> flight.getBasePriceEconomy();
    };
  }

  private String generateBookingReference() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    Random random = new Random();
    StringBuilder sb = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }

  private BigDecimal getServicePrice(String serviceType) {
    return switch (serviceType) {
      case "EXTRA_BAGGAGE" -> BigDecimal.valueOf(50);
      case "MEAL" -> BigDecimal.valueOf(20);
      case "TRAVEL_INSURANCE" -> BigDecimal.valueOf(30);
      case "PRIORITY_BOARDING" -> BigDecimal.valueOf(25);
      case "LOUNGE_ACCESS" -> BigDecimal.valueOf(40);
      case "SEAT_SELECTION" -> BigDecimal.valueOf(15);
      default -> BigDecimal.ZERO;
    };
  }

  private void sleepBackoff(int attempt) {
    try {
      Thread.sleep((long) Math.pow(2, attempt) * 100);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new BusinessException("Interrupted during retry", ErrorCodes.VALIDATION_ERROR);
    }
  }

  private BigDecimal calculateRefund(Booking booking) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime earliestFlight =
        booking.getBookingFlights().stream()
            .map(bf -> bf.getDepartureDate().atTime(bf.getDepartureTime()))
            .min(LocalDateTime::compareTo)
            .orElse(now);
    long hoursUntilDeparture = Duration.between(now, earliestFlight).toHours();
    if (hoursUntilDeparture > 24) {
      return booking.getTotalAmount().multiply(BigDecimal.valueOf(0.9));
    } else if (hoursUntilDeparture > 2) {
      return booking.getTotalAmount().multiply(BigDecimal.valueOf(0.5));
    } else {
      return BigDecimal.ZERO;
    }
  }

  private void validateBookingRequest(CreateBookingRequest request) {
    if (request.getFlights() == null || request.getFlights().isEmpty()) {
      throw new BusinessException(
          "At least one flight must be selected", ErrorCodes.VALIDATION_ERROR);
    }
    if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
      throw new BusinessException(
          "At least one passenger is required", ErrorCodes.VALIDATION_ERROR);
    }
    if (request.getFlights().size() > 5) {
      throw new BusinessException("Maximum 5 flight segments allowed", ErrorCodes.VALIDATION_ERROR);
    }
  }
}
