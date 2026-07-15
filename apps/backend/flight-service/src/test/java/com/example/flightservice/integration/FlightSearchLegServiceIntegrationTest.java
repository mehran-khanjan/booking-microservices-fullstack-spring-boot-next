package com.example.flightservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.flightservice.config.TestSecurityConfig;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchRequestDto;
import com.example.flightservice.entity.*;
import com.example.flightservice.mapper.FlightMapper;
import com.example.flightservice.repository.AirportRepository;
import com.example.flightservice.repository.FlightRepository;
import com.example.flightservice.service.FlightSearchLegService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
class FlightSearchLegServiceIntegrationTest {

  @Autowired private FlightRepository flightRepository;
  @Autowired private AirportRepository airportRepository;
  @Autowired private FlightMapper flightMapper;
  @Autowired private FlightSearchLegService legService;
  @Autowired private EntityManager entityManager;

  @MockitoBean private StringRedisTemplate redisTemplate;

  private Airport origin;
  private Airport destination;
  private Airline airline;
  private AircraftType aircraftType;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(null);

    airline = new Airline();
    airline.setCode("AA");
    airline.setName("American Airlines");
    airline.setCountry("US");
    airline = entityManager.merge(airline);

    origin = new Airport();
    origin.setIataCode("JFK");
    origin.setName("John F Kennedy");
    origin.setCity("New York");
    origin.setCountry("US");
    origin = airportRepository.save(origin);

    destination = new Airport();
    destination.setIataCode("LAX");
    destination.setName("Los Angeles");
    destination.setCity("Los Angeles");
    destination.setCountry("US");
    destination = airportRepository.save(destination);

    aircraftType = new AircraftType();
    aircraftType.setModel("Boeing 737");
    aircraftType.setManufacturer("Boeing");
    aircraftType.setTotalSeats(200);
    aircraftType.setEconomySeats(150);
    aircraftType.setBusinessSeats(20);
    aircraftType = entityManager.merge(aircraftType);
  }

  @Test
  void shouldFindFlightsByRouteAndDate() {
    Flight flight = new Flight();
    flight.setFlightNumber("AA123");
    flight.setAirline(airline);
    flight.setOriginAirport(origin);
    flight.setDestinationAirport(destination);
    flight.setAircraftType(aircraftType);
    flight.setDepartureDate(LocalDate.of(2025, 8, 1));
    flight.setDepartureTime(LocalTime.of(10, 0));
    flight.setArrivalDate(LocalDate.of(2025, 8, 1));
    flight.setArrivalTime(LocalTime.of(13, 0));
    flight.setDurationMinutes(180);
    flight.setBasePriceEconomy(BigDecimal.valueOf(200));
    flight.setCurrency("USD");
    flight.setTotalSeats(200);
    flight.setAvailableSeats(50);
    flight.setEconomyAvailable(30);
    flight.setBusinessAvailable(5);
    flight.setStatus(Flight.FlightStatus.SCHEDULED);
    flightRepository.save(flight);

    FlightSearchRequestDto request = FlightSearchRequestDto.builder()
        .origin("JFK").destination("LAX")
        .departureDate(LocalDate.of(2025, 8, 1))
        .passengers(1).cabinClass("ECONOMY")
        .page(0).size(20).sortBy("PRICE_ASC")
        .build();

    var result = legService.searchLeg("JFK", "LAX", LocalDate.of(2025, 8, 1), request, 0, 20, "PRICE_ASC");

    assertThat(result).isNotNull();
    assertThat(result.getFlights()).hasSize(1);
    assertThat(result.getFlights().get(0).getFlightNumber()).isEqualTo("AA123");
    assertThat(result.getMetadata().getOrigin()).isEqualTo("JFK");
    assertThat(result.getMetadata().getDestination()).isEqualTo("LAX");
  }

  @Test
  void shouldReturnEmptyWhenNoFlightsMatch() {
    FlightSearchRequestDto request = FlightSearchRequestDto.builder()
        .origin("JFK").destination("LAX")
        .departureDate(LocalDate.of(2025, 8, 1))
        .passengers(1)
        .page(0).size(20).sortBy("PRICE_ASC")
        .build();

    var result = legService.searchLeg("JFK", "LAX", LocalDate.of(2025, 8, 1), request, 0, 20, "PRICE_ASC");

    assertThat(result).isNotNull();
    assertThat(result.getFlights()).isEmpty();
    assertThat(result.getPageInfo().getTotalElements()).isZero();
  }
}