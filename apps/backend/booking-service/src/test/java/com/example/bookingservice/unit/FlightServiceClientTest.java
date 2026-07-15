package com.example.bookingservice.unit;

import com.example.bookingservice.entity.Flight;
import com.example.bookingservice.servcie.FlightServiceClient;
import com.example.flightservice.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServiceClientTest {

  @Mock private FlightServiceGrpc.FlightServiceBlockingStub flightStub;

  private FlightServiceClient client;

  @BeforeEach
  void setUp() {
    client = new FlightServiceClient(flightStub);
  }

  @Test
  void shouldGetFlights() {
    com.example.flightservice.grpc.Flight protoFlight =
        com.example.flightservice.grpc.Flight.newBuilder()
            .setId(1L)
            .setFlightNumber("AA123")
            .setAirlineId(10L)
            .setAirlineName("American Airlines")
            .setOriginAirportId(100L)
            .setOriginAirportCode("JFK")
            .setDestinationAirportId(200L)
            .setDestinationAirportCode("LAX")
            .setDepartureDate("2025-02-01")
            .setDepartureTime("10:00")
            .setArrivalDate("2025-02-01")
            .setArrivalTime("13:00")
            .setStatus("SCHEDULED")
            .setAvailableSeats(50)
            .setEconomyAvailable(30)
            .setPremiumEconomyAvailable(10)
            .setBusinessAvailable(8)
            .setFirstClassAvailable(2)
            .setBasePriceEconomy(200.0)
            .setBasePricePremiumEconomy(350.0)
            .setBasePriceBusiness(600.0)
            .setBasePriceFirstClass(1000.0)
            .build();

    FlightResponse response = FlightResponse.newBuilder()
        .addAllFlights(List.of(protoFlight))
        .build();

    when(flightStub.getFlights(any(FlightRequest.class))).thenReturn(response);

    List<Flight> flights = client.getFlights(List.of(1L));

    assertThat(flights).hasSize(1);
    Flight flight = flights.get(0);
    assertThat(flight.getId()).isEqualTo(1L);
    assertThat(flight.getFlightNumber()).isEqualTo("AA123");
    assertThat(flight.getAirline().getName()).isEqualTo("American Airlines");
    assertThat(flight.getOriginAirport().getIataCode()).isEqualTo("JFK");
    assertThat(flight.getDestinationAirport().getIataCode()).isEqualTo("LAX");
    assertThat(flight.getStatus()).isEqualTo(Flight.FlightStatus.SCHEDULED);
    assertThat(flight.getBasePriceEconomy()).isEqualByComparingTo(BigDecimal.valueOf(200));
  }

  @Test
  void shouldReserveSeats() {
    ReserveResponse response = ReserveResponse.newBuilder().setSuccess(true).build();
    when(flightStub.reserveSeats(any(ReserveRequest.class))).thenReturn(response);

    boolean result = client.reserveSeats(1L, "ABC123", 2, Map.of("ECONOMY", 2));

    assertThat(result).isTrue();
    verify(flightStub).reserveSeats(argThat(req ->
        req.getFlightId() == 1L &&
        req.getBookingReference().equals("ABC123") &&
        req.getTotalSeats() == 2
    ));
  }

  @Test
  void shouldReturnFalseWhenReserveFails() {
    ReserveResponse response = ReserveResponse.newBuilder().setSuccess(false).build();
    when(flightStub.reserveSeats(any(ReserveRequest.class))).thenReturn(response);

    boolean result = client.reserveSeats(1L, "ABC123", 2, Map.of("ECONOMY", 2));

    assertThat(result).isFalse();
  }

  @Test
  void shouldReleaseSeats() {
    ReleaseResponse response = ReleaseResponse.newBuilder().setSuccess(true).build();
    when(flightStub.releaseSeats(any(ReleaseRequest.class))).thenReturn(response);

    boolean result = client.releaseSeats(1L, "ABC123", 2, Map.of("ECONOMY", 2));

    assertThat(result).isTrue();
    verify(flightStub).releaseSeats(argThat(req ->
        req.getFlightId() == 1L &&
        req.getBookingReference().equals("ABC123")
    ));
  }

  @Test
  void shouldReturnFalseWhenReleaseFails() {
    ReleaseResponse response = ReleaseResponse.newBuilder().setSuccess(false).build();
    when(flightStub.releaseSeats(any(ReleaseRequest.class))).thenReturn(response);

    boolean result = client.releaseSeats(1L, "ABC123", 2, Map.of("ECONOMY", 2));

    assertThat(result).isFalse();
  }
}