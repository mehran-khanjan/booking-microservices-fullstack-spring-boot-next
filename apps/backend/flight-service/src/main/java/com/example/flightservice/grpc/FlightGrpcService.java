package com.example.flightservice.grpc;

import com.example.flightservice.entity.Flight;
import com.example.flightservice.repository.FlightRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.hibernate.LazyInitializationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * gRPC service implementation for flight operations. Provides methods to retrieve flight details,
 * reserve seats, and release seats. All methods are transactional and handle exceptions by
 * returning appropriate gRPC statuses.
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class FlightGrpcService extends FlightServiceGrpc.FlightServiceImplBase {

  private final FlightRepository flightRepository;

  /**
   * Retrieves flight information for a list of flight IDs.
   *
   * @param request the request containing the list of flight IDs
   * @param responseObserver the observer to send the response or error
   */
  @Override
  @Transactional
  public void getFlights(FlightRequest request, StreamObserver<FlightResponse> responseObserver) {
    log.info("Received getFlights request with ids: {}", request.getFlightIdsList());
    try {
      List<Long> ids = request.getFlightIdsList();
      if (ids.isEmpty()) {
        log.warn("getFlights called with empty id list");
        FlightResponse emptyResponse = FlightResponse.newBuilder().build();
        responseObserver.onNext(emptyResponse);
        responseObserver.onCompleted();
        return;
      }

      log.debug("Fetching flights from repository...");
      List<Flight> flightEntities = flightRepository.findAllById(ids);
      log.info("Found {} flight entities", flightEntities.size());

      List<com.example.flightservice.grpc.Flight> flightProtos =
          flightEntities.stream().map(this::toProto).collect(Collectors.toList());

      FlightResponse response = FlightResponse.newBuilder().addAllFlights(flightProtos).build();

      log.info("getFlights successful, returning {} flights", flightProtos.size());
      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (LazyInitializationException e) {
      log.error("LazyInitializationException in getFlights – associations not fetched", e);
      responseObserver.onError(
          Status.INTERNAL
              .withDescription("Lazy loading failed: " + e.getMessage())
              .asRuntimeException());
    } catch (Exception e) {
      log.error("Error getting flights", e);
      String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
      responseObserver.onError(Status.INTERNAL.withDescription(msg).asRuntimeException());
    }
  }

  /**
   * Reserves seats on a flight for specified cabin classes. Uses pessimistic locking to prevent
   * concurrent seat modifications. Validates flight status and seat availability before updating.
   *
   * @param request the request containing flight ID, total seats, and cabin seat counts
   * @param responseObserver the observer to send the reservation result or error
   */
  @Override
  @Transactional
  public void reserveSeats(
      ReserveRequest request, StreamObserver<ReserveResponse> responseObserver) {
    log.info(
        "Received reserveSeats request: flightId={}, totalSeats={}, cabinSeats={}",
        request.getFlightId(),
        request.getTotalSeats(),
        request.getCabinSeatsMap());
    try {
      long flightId = request.getFlightId();
      int totalSeats = request.getTotalSeats();
      Map<String, Integer> cabinSeats = request.getCabinSeatsMap();

      int economy = cabinSeats.getOrDefault("ECONOMY", 0);
      int premium = cabinSeats.getOrDefault("PREMIUM_ECONOMY", 0);
      int business = cabinSeats.getOrDefault("BUSINESS", 0);
      int first = cabinSeats.getOrDefault("FIRST_CLASS", 0);

      log.debug("Acquiring pessimistic lock for flight {}", flightId);
      Flight flight =
          flightRepository
              .findByIdWithPessimisticLock(flightId)
              .orElseThrow(() -> new RuntimeException("Flight not found: " + flightId));

      log.debug(
          "Flight status: {}, available seats: total={}, economy={}, premium={}, business={}, first={}",
          flight.getStatus(),
          flight.getAvailableSeats(),
          flight.getEconomyAvailable(),
          flight.getPremiumEconomyAvailable(),
          flight.getBusinessAvailable(),
          flight.getFirstClassAvailable());

      if (flight.getStatus() != Flight.FlightStatus.SCHEDULED) {
        throw new RuntimeException("Flight not available for booking");
      }
      if (flight.getAvailableSeats() < totalSeats) {
        throw new RuntimeException("Insufficient total seats");
      }
      if (flight.getEconomyAvailable() < economy
          || flight.getPremiumEconomyAvailable() < premium
          || flight.getBusinessAvailable() < business
          || flight.getFirstClassAvailable() < first) {
        throw new RuntimeException("Insufficient seats in one or more cabin classes");
      }

      log.info(
          "Reserving seats: total={}, economy={}, premium={}, business={}, first={}",
          totalSeats,
          economy,
          premium,
          business,
          first);
      int updated =
          flightRepository.reserveSeats(flightId, totalSeats, economy, premium, business, first);

      if (updated == 0) {
        throw new RuntimeException("Seat reservation failed due to concurrent modification");
      }

      ReserveResponse response =
          ReserveResponse.newBuilder()
              .setSuccess(true)
              .setMessage("Seats reserved successfully")
              .setReservedSeats(totalSeats)
              .build();
      log.info("reserveSeats successful for flight {}", flightId);
      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (Exception e) {
      log.error("Error reserving seats", e);
      String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
      responseObserver.onError(Status.INTERNAL.withDescription(msg).asRuntimeException());
    }
  }

  /**
   * Releases previously reserved seats on a flight. Increases available seat counts for the
   * specified cabin classes.
   *
   * @param request the request containing flight ID, total seats, and cabin seat counts
   * @param responseObserver the observer to send the release result or error
   */
  @Override
  @Transactional
  public void releaseSeats(
      ReleaseRequest request, StreamObserver<ReleaseResponse> responseObserver) {
    log.info(
        "Received releaseSeats request: flightId={}, totalSeats={}, cabinSeats={}",
        request.getFlightId(),
        request.getTotalSeats(),
        request.getCabinSeatsMap());
    try {
      long flightId = request.getFlightId();
      int totalSeats = request.getTotalSeats();
      Map<String, Integer> cabinSeats = request.getCabinSeatsMap();

      int economy = cabinSeats.getOrDefault("ECONOMY", 0);
      int premium = cabinSeats.getOrDefault("PREMIUM_ECONOMY", 0);
      int business = cabinSeats.getOrDefault("BUSINESS", 0);
      int first = cabinSeats.getOrDefault("FIRST_CLASS", 0);

      log.info(
          "Releasing seats: total={}, economy={}, premium={}, business={}, first={}",
          totalSeats,
          economy,
          premium,
          business,
          first);
      int updated =
          flightRepository.releaseSeats(flightId, totalSeats, economy, premium, business, first);

      if (updated == 0) {
        throw new RuntimeException("Failed to release seats, flight not found");
      }

      ReleaseResponse response =
          ReleaseResponse.newBuilder()
              .setSuccess(true)
              .setMessage("Seats released successfully")
              .build();
      log.info("releaseSeats successful for flight {}", flightId);
      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (Exception e) {
      log.error("Error releasing seats", e);
      String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
      responseObserver.onError(Status.INTERNAL.withDescription(msg).asRuntimeException());
    }
  }

  /**
   * Converts a JPA Flight entity to a protobuf Flight message. Handles null associations gracefully
   * by substituting default values (0, "UNK", "Unknown", etc.). Logs warnings for null
   * associations.
   *
   * @param f the JPA Flight entity, must not be null
   * @return the protobuf Flight message
   * @throws RuntimeException if conversion fails, typically due to unexpected exceptions
   */
  private com.example.flightservice.grpc.Flight toProto(Flight f) {
    try {
      log.debug("Converting flight id={}, number={}", f.getId(), f.getFlightNumber());

      if (f.getAirline() == null) {
        log.warn("Flight {} has null airline", f.getId());
      }
      if (f.getOriginAirport() == null) {
        log.warn("Flight {} has null originAirport", f.getId());
      }
      if (f.getDestinationAirport() == null) {
        log.warn("Flight {} has null destinationAirport", f.getId());
      }

      return com.example.flightservice.grpc.Flight.newBuilder()
          .setId(f.getId())
          .setFlightNumber(f.getFlightNumber())
          .setAirlineId(f.getAirline() != null ? f.getAirline().getId() : 0L)
          .setAirlineName(f.getAirline() != null ? f.getAirline().getName() : "Unknown")
          .setOriginAirportId(f.getOriginAirport() != null ? f.getOriginAirport().getId() : 0L)
          .setOriginAirportCode(
              f.getOriginAirport() != null ? f.getOriginAirport().getIataCode() : "UNK")
          .setDestinationAirportId(
              f.getDestinationAirport() != null ? f.getDestinationAirport().getId() : 0L)
          .setDestinationAirportCode(
              f.getDestinationAirport() != null ? f.getDestinationAirport().getIataCode() : "UNK")
          .setDepartureDate(f.getDepartureDate() != null ? f.getDepartureDate().toString() : "")
          .setDepartureTime(f.getDepartureTime() != null ? f.getDepartureTime().toString() : "")
          .setArrivalDate(f.getArrivalDate() != null ? f.getArrivalDate().toString() : "")
          .setArrivalTime(f.getArrivalTime() != null ? f.getArrivalTime().toString() : "")
          .setStatus(f.getStatus() != null ? f.getStatus().name() : "UNKNOWN")
          .setAvailableSeats(f.getAvailableSeats() != null ? f.getAvailableSeats() : 0)
          .setEconomyAvailable(f.getEconomyAvailable() != null ? f.getEconomyAvailable() : 0)
          .setPremiumEconomyAvailable(
              f.getPremiumEconomyAvailable() != null ? f.getPremiumEconomyAvailable() : 0)
          .setBusinessAvailable(f.getBusinessAvailable() != null ? f.getBusinessAvailable() : 0)
          .setFirstClassAvailable(
              f.getFirstClassAvailable() != null ? f.getFirstClassAvailable() : 0)
          .setBasePriceEconomy(
              f.getBasePriceEconomy() != null ? f.getBasePriceEconomy().doubleValue() : 0.0)
          .setBasePricePremiumEconomy(
              f.getBasePricePremiumEconomy() != null
                  ? f.getBasePricePremiumEconomy().doubleValue()
                  : 0.0)
          .setBasePriceBusiness(
              f.getBasePriceBusiness() != null ? f.getBasePriceBusiness().doubleValue() : 0.0)
          .setBasePriceFirstClass(
              f.getBasePriceFirstClass() != null ? f.getBasePriceFirstClass().doubleValue() : 0.0)
          .build();
    } catch (Exception e) {
      log.error("Error converting flight entity to proto for flight id={}", f.getId(), e);
      throw new RuntimeException("Failed to convert flight " + f.getId(), e);
    }
  }
}
