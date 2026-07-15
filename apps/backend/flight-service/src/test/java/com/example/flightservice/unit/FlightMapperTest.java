package com.example.flightservice.unit;

import com.example.flightservice.dto.controller.flightsearch.FlightResponseDto;
import com.example.flightservice.entity.*;
import com.example.flightservice.mapper.FlightMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class FlightMapperTest {

  private FlightMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new FlightMapper();
  }

  @Test
  void shouldMapFlightToResponse() {
    Flight flight = createFullFlight();

    FlightResponseDto dto = mapper.toResponse(flight);

    assertThat(dto).isNotNull();
    assertThat(dto.getId()).isEqualTo(1L);
    assertThat(dto.getFlightNumber()).isEqualTo("AA123");
    assertThat(dto.getAirline().getCode()).isEqualTo("AA");
    assertThat(dto.getAirline().getName()).isEqualTo("American Airlines");
    assertThat(dto.getOrigin().getIataCode()).isEqualTo("JFK");
    assertThat(dto.getDestination().getIataCode()).isEqualTo("LAX");
    assertThat(dto.getDepartureDate()).isEqualTo(LocalDate.of(2025, 6, 1));
    assertThat(dto.getDepartureTime()).isEqualTo(LocalTime.of(10, 0));
    assertThat(dto.getArrivalDate()).isEqualTo(LocalDate.of(2025, 6, 1));
    assertThat(dto.getArrivalTime()).isEqualTo(LocalTime.of(13, 0));
    assertThat(dto.getDurationMinutes()).isEqualTo(180);
    assertThat(dto.getDurationFormatted()).isEqualTo("3h 00m");
    assertThat(dto.getPricing().getEconomy()).isEqualByComparingTo(BigDecimal.valueOf(200));
    assertThat(dto.getPricing().getBusiness()).isEqualByComparingTo(BigDecimal.valueOf(600));
    assertThat(dto.getPricing().getCurrency()).isEqualTo("USD");
    assertThat(dto.getAircraftType()).isEqualTo("Boeing 737-800");
    assertThat(dto.getTotalSeats()).isEqualTo(200);
    assertThat(dto.getAvailableSeats()).isEqualTo(50);
    assertThat(dto.getSeatAvailability().getEconomy()).isEqualTo(30);
    assertThat(dto.getSeatAvailability().getBusiness()).isEqualTo(5);
    assertThat(dto.getStatus()).isEqualTo("SCHEDULED");
    assertThat(dto.getBaggageAllowanceKg()).isEqualTo(23);
    assertThat(dto.getCabinBaggageAllowanceKg()).isEqualTo(7);
    assertThat(dto.getCancellationPolicy()).isEqualTo("Non-refundable");
  }

  @Test
  void shouldFormatDurationCorrectly() {
    Flight flight = createFullFlight();
    flight.setDurationMinutes(150);

    FlightResponseDto dto = mapper.toResponse(flight);

    assertThat(dto.getDurationFormatted()).isEqualTo("2h 30m");
  }

  @Test
  void shouldHandleShortDuration() {
    Flight flight = createFullFlight();
    flight.setDurationMinutes(45);

    FlightResponseDto dto = mapper.toResponse(flight);

    assertThat(dto.getDurationFormatted()).isEqualTo("0h 45m");
  }

  private Flight createFullFlight() {
    Airline airline = Airline.builder()
        .id(10L).code("AA").name("American Airlines").country("US").logoUrl("https://example.com/aa.png")
        .build();

    Airport origin = Airport.builder()
        .id(100L).iataCode("JFK").name("John F Kennedy").city("New York").country("US").timezone("America/New_York")
        .build();

    Airport dest = Airport.builder()
        .id(200L).iataCode("LAX").name("Los Angeles").city("Los Angeles").country("US").timezone("America/Los_Angeles")
        .build();

    AircraftType aircraft = AircraftType.builder()
        .id(300L).model("Boeing 737-800").manufacturer("Boeing").totalSeats(200)
        .economySeats(150).businessSeats(20).build();

    return Flight.builder()
        .id(1L).flightNumber("AA123")
        .airline(airline).originAirport(origin).destinationAirport(dest).aircraftType(aircraft)
        .departureDate(LocalDate.of(2025, 6, 1)).departureTime(LocalTime.of(10, 0))
        .arrivalDate(LocalDate.of(2025, 6, 1)).arrivalTime(LocalTime.of(13, 0))
        .durationMinutes(180)
        .basePriceEconomy(BigDecimal.valueOf(200))
        .basePricePremiumEconomy(BigDecimal.valueOf(350))
        .basePriceBusiness(BigDecimal.valueOf(600))
        .basePriceFirstClass(BigDecimal.valueOf(1000))
        .currency("USD")
        .totalSeats(200).availableSeats(50)
        .economyAvailable(30).premiumEconomyAvailable(10)
        .businessAvailable(5).firstClassAvailable(2)
        .status(Flight.FlightStatus.SCHEDULED)
        .baggageAllowanceKg(23).cabinBaggageAllowanceKg(7)
        .cancellationPolicy("Non-refundable")
        .build();
  }
}