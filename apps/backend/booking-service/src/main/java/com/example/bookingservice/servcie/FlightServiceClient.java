package com.example.bookingservice.servcie;

import com.example.bookingservice.entity.Airline;
import com.example.bookingservice.entity.Airport;
import com.example.bookingservice.entity.Flight;
import com.example.flightservice.grpc.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Client for interacting with the Flight gRPC service. Provides methods to retrieve flight details,
 * reserve seats, and release seats.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlightServiceClient {

  private final FlightServiceGrpc.FlightServiceBlockingStub flightStub;

  /**
   * Retrieves flight details for a list of flight IDs.
   *
   * @param flightIds the list of flight IDs
   * @return a list of Flight entities
   */
  public List<Flight> getFlights(List<Long> flightIds) {
    FlightRequest request =
        FlightRequest.newBuilder().addAllFlightIds(flightIds).setIncludeAvailability(true).build();
    FlightResponse response = flightStub.getFlights(request);
    return response.getFlightsList().stream()
        .map(this::toFlightEntity)
        .collect(Collectors.toList());
  }

  /**
   * Reserves seats on a flight for a booking.
   *
   * @param flightId the flight identifier
   * @param bookingRef the booking reference for correlation
   * @param totalSeats total number of seats to reserve
   * @param cabinSeats map of cabin class to number of seats
   * @return true if reservation succeeded, false otherwise
   */
  public boolean reserveSeats(
      Long flightId, String bookingRef, int totalSeats, Map<String, Integer> cabinSeats) {
    ReserveRequest request =
        ReserveRequest.newBuilder()
            .setFlightId(flightId)
            .setBookingReference(bookingRef)
            .setTotalSeats(totalSeats)
            .putAllCabinSeats(cabinSeats)
            .build();
    ReserveResponse response = flightStub.reserveSeats(request);
    return response.getSuccess();
  }

  /**
   * Releases previously reserved seats on a flight.
   *
   * @param flightId the flight identifier
   * @param bookingRef the booking reference
   * @param totalSeats total number of seats to release
   * @param cabinSeats map of cabin class to number of seats
   * @return true if release succeeded, false otherwise
   */
  public boolean releaseSeats(
      Long flightId, String bookingRef, int totalSeats, Map<String, Integer> cabinSeats) {
    ReleaseRequest request =
        ReleaseRequest.newBuilder()
            .setFlightId(flightId)
            .setBookingReference(bookingRef)
            .setTotalSeats(totalSeats)
            .putAllCabinSeats(cabinSeats)
            .build();
    ReleaseResponse response = flightStub.releaseSeats(request);
    return response.getSuccess();
  }

  /**
   * Converts a protobuf Flight message to the local JPA Flight entity.
   *
   * @param protoFlight the protobuf message
   * @return a Flight entity populated with the protobuf data
   */
  private Flight toFlightEntity(com.example.flightservice.grpc.Flight protoFlight) {
    Flight flight = new Flight();
    flight.setId(protoFlight.getId());
    flight.setFlightNumber(protoFlight.getFlightNumber());

    Airline airline = new Airline();
    airline.setId(protoFlight.getAirlineId());
    airline.setName(protoFlight.getAirlineName());
    flight.setAirline(airline);

    Airport origin = new Airport();
    origin.setId(protoFlight.getOriginAirportId());
    origin.setIataCode(protoFlight.getOriginAirportCode());
    flight.setOriginAirport(origin);

    Airport destination = new Airport();
    destination.setId(protoFlight.getDestinationAirportId());
    destination.setIataCode(protoFlight.getDestinationAirportCode());
    flight.setDestinationAirport(destination);

    flight.setDepartureDate(LocalDate.parse(protoFlight.getDepartureDate()));
    flight.setDepartureTime(LocalTime.parse(protoFlight.getDepartureTime()));
    flight.setArrivalDate(LocalDate.parse(protoFlight.getArrivalDate()));
    flight.setArrivalTime(LocalTime.parse(protoFlight.getArrivalTime()));

    flight.setStatus(Flight.FlightStatus.valueOf(protoFlight.getStatus()));

    flight.setAvailableSeats(protoFlight.getAvailableSeats());
    flight.setEconomyAvailable(protoFlight.getEconomyAvailable());
    flight.setPremiumEconomyAvailable(protoFlight.getPremiumEconomyAvailable());
    flight.setBusinessAvailable(protoFlight.getBusinessAvailable());
    flight.setFirstClassAvailable(protoFlight.getFirstClassAvailable());

    flight.setBasePriceEconomy(BigDecimal.valueOf(protoFlight.getBasePriceEconomy()));
    flight.setBasePricePremiumEconomy(BigDecimal.valueOf(protoFlight.getBasePricePremiumEconomy()));
    flight.setBasePriceBusiness(BigDecimal.valueOf(protoFlight.getBasePriceBusiness()));
    flight.setBasePriceFirstClass(BigDecimal.valueOf(protoFlight.getBasePriceFirstClass()));

    return flight;
  }
}
