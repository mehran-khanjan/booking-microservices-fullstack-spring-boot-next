package com.example.bookingservice.unit;

import com.example.bookingservice.config.LockingStrategyConfig;
import com.example.bookingservice.constant.ErrorCodes;
import com.example.bookingservice.dto.controller.BookingResponse;
import com.example.bookingservice.dto.controller.CreateBookingRequest;
import com.example.bookingservice.entity.*;
import com.example.bookingservice.enums.CabinClass;
import com.example.bookingservice.exception.BusinessException;
import com.example.bookingservice.mapper.BookingMapper;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.servcie.BookingLockingService;
import com.example.bookingservice.servcie.BookingService;
import com.example.bookingservice.servcie.FlightServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

  @Mock private LockingStrategyConfig lockingConfig;
  @Mock private BookingLockingService lockingService;
  @Mock private BookingRepository bookingRepository;
  @Mock private BookingMapper bookingMapper;
  @Mock private FlightServiceClient flightServiceClient;

  private BookingService bookingService;

  @Captor private ArgumentCaptor<Booking> bookingCaptor;

  private CreateBookingRequest validRequest;
  private Flight flight;
  private Map<Long, Flight> flightsById;

  @BeforeEach
  void setUp() {
    bookingService = new BookingService(lockingConfig, lockingService, bookingRepository, bookingMapper, flightServiceClient);

    when(bookingMapper.toPassengerEntity(any())).thenAnswer(invocation -> {
      CreateBookingRequest.PassengerDetails details = invocation.getArgument(0);
      Passenger passenger = new Passenger();
      passenger.setFirstName(details.getFirstName());
      passenger.setLastName(details.getLastName());
      passenger.setDateOfBirth(details.getDateOfBirth());
      return passenger;
    });

    flight = new Flight();
    flight.setId(1L);
    flight.setFlightNumber("AA123");
    flight.setStatus(Flight.FlightStatus.SCHEDULED);
    flight.setEconomyAvailable(50);
    flight.setPremiumEconomyAvailable(10);
    flight.setBusinessAvailable(5);
    flight.setFirstClassAvailable(2);
    flight.setAvailableSeats(67);
    flight.setBasePriceEconomy(BigDecimal.valueOf(200));
    flight.setBasePricePremiumEconomy(BigDecimal.valueOf(350));
    flight.setBasePriceBusiness(BigDecimal.valueOf(600));
    flight.setBasePriceFirstClass(BigDecimal.valueOf(1000));

    Airline airline = new Airline();
    airline.setId(1L);
    airline.setName("Test Airlines");
    flight.setAirline(airline);

    Airport origin = new Airport();
    origin.setId(1L);
    origin.setIataCode("JFK");
    flight.setOriginAirport(origin);

    Airport dest = new Airport();
    dest.setId(2L);
    dest.setIataCode("LAX");
    flight.setDestinationAirport(dest);

    flight.setDepartureDate(LocalDate.now().plusDays(7));
    flight.setDepartureTime(LocalTime.of(10, 0));
    flight.setArrivalDate(LocalDate.now().plusDays(7));
    flight.setArrivalTime(LocalTime.of(13, 0));

    flightsById = Map.of(1L, flight);

    validRequest = CreateBookingRequest.builder()
        .flights(List.of(
            CreateBookingRequest.FlightSelection.builder()
                .flightId(1L)
                .segmentOrder(0)
                .cabinClass("ECONOMY")
                .build()
        ))
        .passengers(List.of(
            CreateBookingRequest.PassengerDetails.builder()
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build()
        ))
        .contactEmail("john@example.com")
        .contactPhone("+1234567890")
        .currency("USD")
        .build();
  }

  @Nested
  class CreateBooking {

    @Test
    void shouldCreateBookingSuccessfullyWithDistributedLock() {
      when(lockingConfig.getStrategy()).thenReturn("DISTRIBUTED");
      when(lockingService.executeWithMultiLock(anyList(), anyLong(), anyLong(), any(Supplier.class)))
          .thenAnswer(invocation -> {
            Supplier<BookingResponse> supplier = invocation.getArgument(3);
            return supplier.get();
          });
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
      when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
          .thenReturn(true);

      Booking savedBooking = createSampleBooking();
      when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

      BookingResponse expectedResponse = createSampleResponse();
      when(bookingMapper.toResponse(any(Booking.class))).thenReturn(expectedResponse);

      BookingResponse result = bookingService.createBooking(validRequest, "user-123");

      assertThat(result).isNotNull();
      assertThat(result.getBookingReference()).isEqualTo("ABC123");
      assertThat(result.getStatus()).isEqualTo("PENDING_PAYMENT");
      assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(230));

      verify(lockingService).executeWithMultiLock(anyList(), anyLong(), anyLong(), any(Supplier.class));
      verify(flightServiceClient).getFlights(anyList());
      verify(flightServiceClient).reserveSeats(eq(1L), anyString(), eq(1), anyMap());
      verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void shouldCreateBookingSuccessfullyWithOptimisticLock() {
      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
      when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
          .thenReturn(true);

      Booking savedBooking = createSampleBooking();
      when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

      BookingResponse expectedResponse = createSampleResponse();
      when(bookingMapper.toResponse(any(Booking.class))).thenReturn(expectedResponse);

      BookingResponse result = bookingService.createBooking(validRequest, "user-123");

      assertThat(result).isNotNull();
      assertThat(result.getBookingReference()).isEqualTo("ABC123");
      verify(lockingService, never()).executeWithMultiLock(anyList(), anyLong(), anyLong(), any(Supplier.class));
      verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void shouldThrowWhenNoFlightsProvided() {
      CreateBookingRequest invalid = CreateBookingRequest.builder()
          .flights(null)
          .passengers(List.of(
              CreateBookingRequest.PassengerDetails.builder()
                  .firstName("John")
                  .lastName("Doe")
                  .dateOfBirth(LocalDate.of(1990, 1, 1))
                  .build()
          ))
          .contactEmail("john@example.com")
          .build();

      assertThatThrownBy(() -> bookingService.createBooking(invalid, "user-123"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("At least one flight");
    }

    @Test
    void shouldThrowWhenNoPassengersProvided() {
      CreateBookingRequest invalid = CreateBookingRequest.builder()
          .flights(List.of(
              CreateBookingRequest.FlightSelection.builder()
                  .flightId(1L)
                  .segmentOrder(0)
                  .cabinClass("ECONOMY")
                  .build()
          ))
          .passengers(null)
          .contactEmail("john@example.com")
          .build();

      assertThatThrownBy(() -> bookingService.createBooking(invalid, "user-123"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("At least one passenger");
    }

    @Test
    void shouldThrowWhenMoreThan5Flights() {
      List<CreateBookingRequest.FlightSelection> flights = List.of(
          CreateBookingRequest.FlightSelection.builder().flightId(1L).segmentOrder(0).cabinClass("ECONOMY").build(),
          CreateBookingRequest.FlightSelection.builder().flightId(2L).segmentOrder(1).cabinClass("ECONOMY").build(),
          CreateBookingRequest.FlightSelection.builder().flightId(3L).segmentOrder(2).cabinClass("ECONOMY").build(),
          CreateBookingRequest.FlightSelection.builder().flightId(4L).segmentOrder(3).cabinClass("ECONOMY").build(),
          CreateBookingRequest.FlightSelection.builder().flightId(5L).segmentOrder(4).cabinClass("ECONOMY").build(),
          CreateBookingRequest.FlightSelection.builder().flightId(6L).segmentOrder(5).cabinClass("ECONOMY").build()
      );
      CreateBookingRequest invalid = CreateBookingRequest.builder()
          .flights(flights)
          .passengers(List.of(
              CreateBookingRequest.PassengerDetails.builder()
                  .firstName("John").lastName("Doe")
                  .dateOfBirth(LocalDate.of(1990, 1, 1)).build()
          ))
          .contactEmail("john@example.com")
          .build();

      assertThatThrownBy(() -> bookingService.createBooking(invalid, "user-123"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Maximum 5 flight segments");
    }

    @Test
    void shouldThrowWhenFlightNotFound() {
      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of());

      assertThatThrownBy(() -> bookingService.createBooking(validRequest, "user-123"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("could not be found");
    }

    @Test
    void shouldThrowWhenFlightNotScheduled() {
      flight.setStatus(Flight.FlightStatus.CANCELLED);
      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));

      assertThatThrownBy(() -> bookingService.createBooking(validRequest, "user-123"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("not available");
    }

    @Test
    void shouldThrowWhenInsufficientSeats() {
      flight.setEconomyAvailable(0);
      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));

      assertThatThrownBy(() -> bookingService.createBooking(validRequest, "user-123"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Insufficient seats");
    }

    @Test
    void shouldThrowWhenSeatReservationFails() {
      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
      when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
          .thenReturn(false);

      assertThatThrownBy(() -> bookingService.createBooking(validRequest, "user-123"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Booking failed");
    }

    @Test
    void shouldReleaseSeatsWhenReservationFailsForSecondFlight() {
      Flight secondFlight = new Flight();
      secondFlight.setId(2L);
      secondFlight.setFlightNumber("BB456");
      secondFlight.setStatus(Flight.FlightStatus.SCHEDULED);
      secondFlight.setEconomyAvailable(50);
      secondFlight.setBasePriceEconomy(BigDecimal.valueOf(200));
      Airport origin = new Airport();
      origin.setIataCode("LAX");
      secondFlight.setOriginAirport(origin);
      Airport dest = new Airport();
      dest.setIataCode("JFK");
      secondFlight.setDestinationAirport(dest);
      Airline airline = new Airline();
      airline.setName("Test Airlines");
      secondFlight.setAirline(airline);
      secondFlight.setDepartureDate(LocalDate.now().plusDays(10));
      secondFlight.setDepartureTime(LocalTime.of(14, 0));
      secondFlight.setArrivalDate(LocalDate.now().plusDays(10));
      secondFlight.setArrivalTime(LocalTime.of(17, 0));

      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight, secondFlight));
      when(flightServiceClient.reserveSeats(eq(1L), anyString(), anyInt(), anyMap()))
          .thenReturn(true);
      when(flightServiceClient.reserveSeats(eq(2L), anyString(), anyInt(), anyMap()))
          .thenThrow(new RuntimeException("Reservation failed"));

      assertThatThrownBy(() -> {
        CreateBookingRequest multiFlightReq = CreateBookingRequest.builder()
            .flights(List.of(
                CreateBookingRequest.FlightSelection.builder().flightId(1L).segmentOrder(0).cabinClass("ECONOMY").build(),
                CreateBookingRequest.FlightSelection.builder().flightId(2L).segmentOrder(1).cabinClass("ECONOMY").build()
            ))
            .passengers(List.of(
                CreateBookingRequest.PassengerDetails.builder()
                    .firstName("John").lastName("Doe")
                    .dateOfBirth(LocalDate.of(1990, 1, 1)).build()
            ))
            .contactEmail("john@example.com")
            .build();
        bookingService.createBooking(multiFlightReq, "user-123");
      }).isInstanceOf(BusinessException.class);

      verify(flightServiceClient).releaseSeats(eq(1L), anyString(), anyInt(), anyMap());
    }

    @Test
    void shouldHandleBookingReferenceCollision() {
      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
      when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
          .thenReturn(true);

      Booking savedBooking = createSampleBooking();
      when(bookingRepository.save(any(Booking.class)))
          .thenThrow(new DataIntegrityViolationException("duplicate reference"))
          .thenReturn(savedBooking);

      BookingResponse expectedResponse = createSampleResponse();
      when(bookingMapper.toResponse(any(Booking.class))).thenReturn(expectedResponse);

      BookingResponse result = bookingService.createBooking(validRequest, "user-123");

      assertThat(result).isNotNull();
      verify(bookingRepository, times(2)).save(any(Booking.class));
    }

    @Test
    void shouldThrowAfterMaxReferenceCollisions() {
      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
      when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
          .thenReturn(true);
      when(bookingRepository.save(any(Booking.class)))
          .thenThrow(new DataIntegrityViolationException("duplicate"));

      assertThatThrownBy(() -> bookingService.createBooking(validRequest, "user-123"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("reference collisions");

      verify(flightServiceClient).releaseSeats(anyLong(), anyString(), anyInt(), anyMap());
    }

    @Test
    void shouldThrowOnInvalidLockingStrategy() {
      when(lockingConfig.getStrategy()).thenReturn("UNKNOWN");

      assertThatThrownBy(() -> bookingService.createBooking(validRequest, "user-123"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Invalid locking strategy");
    }

    @Test
    void shouldUseDefaultCurrencyWhenNotProvided() {
      validRequest.setCurrency(null);
      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
      when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
          .thenReturn(true);

      Booking savedBooking = createSampleBooking();
      savedBooking.setCurrency("USD");
      when(bookingRepository.save(bookingCaptor.capture())).thenReturn(savedBooking);

      BookingResponse expectedResponse = createSampleResponse();
      when(bookingMapper.toResponse(any(Booking.class))).thenReturn(expectedResponse);

      bookingService.createBooking(validRequest, "user-123");

      Booking captured = bookingCaptor.getValue();
      assertThat(captured.getCurrency()).isEqualTo("USD");
    }

    @Test
    void shouldRoundTripBookingTypeForReturnFlight() {
      Flight inbound = new Flight();
      inbound.setId(2L);
      inbound.setFlightNumber("BB456");
      inbound.setStatus(Flight.FlightStatus.SCHEDULED);
      inbound.setEconomyAvailable(50);
      inbound.setBasePriceEconomy(BigDecimal.valueOf(200));
      Airport lax = new Airport();
      lax.setIataCode("LAX");
      Airport jfk = new Airport();
      jfk.setIataCode("JFK");
      inbound.setOriginAirport(lax);
      inbound.setDestinationAirport(jfk);
      Airline airline = new Airline();
      airline.setName("Test Airlines");
      inbound.setAirline(airline);
      inbound.setDepartureDate(LocalDate.now().plusDays(14));
      inbound.setDepartureTime(LocalTime.of(14, 0));
      inbound.setArrivalDate(LocalDate.now().plusDays(14));
      inbound.setArrivalTime(LocalTime.of(17, 0));

      flightsById = Map.of(1L, flight, 2L, inbound);

      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight, inbound));
      when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
          .thenReturn(true);

      Booking savedBooking = createSampleBooking();
      savedBooking.setBookingType(Booking.BookingType.ROUND_TRIP);
      when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

      BookingResponse expectedResponse = createSampleResponse();
      expectedResponse.setBookingType("ROUND_TRIP");
      when(bookingMapper.toResponse(any(Booking.class))).thenReturn(expectedResponse);

      CreateBookingRequest roundTripReq = CreateBookingRequest.builder()
          .flights(List.of(
              CreateBookingRequest.FlightSelection.builder().flightId(1L).segmentOrder(0).cabinClass("ECONOMY").build(),
              CreateBookingRequest.FlightSelection.builder().flightId(2L).segmentOrder(1).cabinClass("ECONOMY").build()
          ))
          .passengers(List.of(
              CreateBookingRequest.PassengerDetails.builder()
                  .firstName("John").lastName("Doe")
                  .dateOfBirth(LocalDate.of(1990, 1, 1)).build()
          ))
          .contactEmail("john@example.com")
          .build();

      BookingResponse result = bookingService.createBooking(roundTripReq, "user-123");

      assertThat(result.getBookingType()).isEqualTo("ROUND_TRIP");
    }

    @Test
    void shouldAddAdditionalServices() {
      validRequest.setAdditionalServices(List.of(
          CreateBookingRequest.AdditionalServiceRequest.builder()
              .serviceType("EXTRA_BAGGAGE")
              .description("Extra checked bag")
              .build(),
          CreateBookingRequest.AdditionalServiceRequest.builder()
              .serviceType("MEAL")
              .description("Special meal")
              .build()
      ));

      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
      when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
          .thenReturn(true);

      Booking savedBooking = createSampleBooking();
      when(bookingRepository.save(bookingCaptor.capture())).thenReturn(savedBooking);

      BookingResponse expectedResponse = createSampleResponse();
      when(bookingMapper.toResponse(any(Booking.class))).thenReturn(expectedResponse);

      bookingService.createBooking(validRequest, "user-123");

      Booking captured = bookingCaptor.getValue();
      // Base price 200 + taxes 30 = 230 for 1 economy flight
      // Plus service prices: EXTRA_BAGGAGE=50, MEAL=20
      assertThat(captured.getTotalAmount())
          .isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void shouldSetGroupBookingHoldFor10OrMorePassengers() {
      List<CreateBookingRequest.PassengerDetails> passengers = List.of(
          CreateBookingRequest.PassengerDetails.builder().firstName("P1").lastName("L1").dateOfBirth(LocalDate.of(1990, 1, 1)).build(),
          CreateBookingRequest.PassengerDetails.builder().firstName("P2").lastName("L2").dateOfBirth(LocalDate.of(1991, 1, 1)).build(),
          CreateBookingRequest.PassengerDetails.builder().firstName("P3").lastName("L3").dateOfBirth(LocalDate.of(1992, 1, 1)).build(),
          CreateBookingRequest.PassengerDetails.builder().firstName("P4").lastName("L4").dateOfBirth(LocalDate.of(1993, 1, 1)).build(),
          CreateBookingRequest.PassengerDetails.builder().firstName("P5").lastName("L5").dateOfBirth(LocalDate.of(1994, 1, 1)).build(),
          CreateBookingRequest.PassengerDetails.builder().firstName("P6").lastName("L6").dateOfBirth(LocalDate.of(1995, 1, 1)).build(),
          CreateBookingRequest.PassengerDetails.builder().firstName("P7").lastName("L7").dateOfBirth(LocalDate.of(1996, 1, 1)).build(),
          CreateBookingRequest.PassengerDetails.builder().firstName("P8").lastName("L8").dateOfBirth(LocalDate.of(1997, 1, 1)).build(),
          CreateBookingRequest.PassengerDetails.builder().firstName("P9").lastName("L9").dateOfBirth(LocalDate.of(1998, 1, 1)).build(),
          CreateBookingRequest.PassengerDetails.builder().firstName("P10").lastName("L10").dateOfBirth(LocalDate.of(1999, 1, 1)).build()
      );

      validRequest.setPassengers(passengers);
      when(lockingConfig.getStrategy()).thenReturn("OPTIMISTIC");
      when(flightServiceClient.getFlights(anyList())).thenReturn(List.of(flight));
      when(flightServiceClient.reserveSeats(anyLong(), anyString(), anyInt(), anyMap()))
          .thenReturn(true);

      Booking savedBooking = createSampleBooking();
      when(bookingRepository.save(bookingCaptor.capture())).thenReturn(savedBooking);

      BookingResponse expectedResponse = createSampleResponse();
      when(bookingMapper.toResponse(any(Booking.class))).thenReturn(expectedResponse);

      bookingService.createBooking(validRequest, "user-123");

      Booking captured = bookingCaptor.getValue();
      assertThat(captured.getHoldExpiresAt()).isNotNull();
    }
  }

  private Booking createSampleBooking() {
    Booking booking = new Booking();
    booking.setId(1L);
    booking.setBookingReference("ABC123");
    booking.setUserId("user-123");
    booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
    booking.setTotalAmount(BigDecimal.valueOf(230));
    booking.setCurrency("USD");
    booking.setContactEmail("john@example.com");
    booking.setContactPhone("+1234567890");
    return booking;
  }

  private BookingResponse createSampleResponse() {
    return BookingResponse.builder()
        .id(1L)
        .bookingReference("ABC123")
        .status("PENDING_PAYMENT")
        .totalAmount(BigDecimal.valueOf(230))
        .currency("USD")
        .contactEmail("john@example.com")
        .contactPhone("+1234567890")
        .build();
  }
}