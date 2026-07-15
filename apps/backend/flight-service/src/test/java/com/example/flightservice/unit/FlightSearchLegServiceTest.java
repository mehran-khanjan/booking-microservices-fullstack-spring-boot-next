package com.example.flightservice.unit;

import com.example.flightservice.service.FlightSearchLegService;
import com.example.flightservice.service.FlightSpecification;
import com.example.flightservice.dto.controller.flightsearch.FlightResponseDto;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchRequestDto;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchResponseDto;
import com.example.flightservice.entity.*;
import com.example.flightservice.exception.BusinessException;
import com.example.flightservice.mapper.FlightMapper;
import com.example.flightservice.repository.AirportRepository;
import com.example.flightservice.repository.FlightRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightSearchLegServiceTest {

  @Mock private FlightRepository flightRepository;
  @Mock private AirportRepository airportRepository;
  @Mock private FlightMapper flightMapper;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private FlightSearchLegService legService;

  @Captor private ArgumentCaptor<Specification<Flight>> specCaptor;

  private Airport origin;
  private Airport destination;
  private Flight flight;
  private FlightResponseDto flightDto;
  private FlightSearchRequestDto request;

  @BeforeEach
  void setUp() {
    legService = new FlightSearchLegService(flightRepository, airportRepository,
        flightMapper, redisTemplate, objectMapper);

    origin = Airport.builder().id(1L).iataCode("JFK").name("John F Kennedy").build();
    destination = Airport.builder().id(2L).iataCode("LAX").name("Los Angeles").build();

    Airline airline = Airline.builder().id(10L).code("AA").name("American Airlines").build();
    AircraftType aircraft = AircraftType.builder().id(20L).model("Boeing 737").build();

    flight = Flight.builder()
        .id(1L).flightNumber("AA123")
        .airline(airline).originAirport(origin).destinationAirport(destination)
        .aircraftType(aircraft)
        .departureDate(LocalDate.of(2025, 6, 1))
        .departureTime(LocalTime.of(10, 0))
        .arrivalDate(LocalDate.of(2025, 6, 1))
        .arrivalTime(LocalTime.of(13, 0))
        .durationMinutes(180)
        .basePriceEconomy(BigDecimal.valueOf(200))
        .currency("USD")
        .totalSeats(200).availableSeats(50)
        .economyAvailable(30).businessAvailable(5)
        .status(Flight.FlightStatus.SCHEDULED)
        .build();

    flightDto = FlightResponseDto.builder()
        .id(1L).flightNumber("AA123")
        .build();

    request = FlightSearchRequestDto.builder()
        .origin("JFK").destination("LAX")
        .departureDate(LocalDate.of(2025, 6, 1))
        .passengers(1).cabinClass("ECONOMY")
        .page(0).size(20).sortBy("PRICE_ASC")
        .build();
  }

  @Nested
  class SearchLeg {

    @Test
    void shouldSearchFlightsSuccessfully() {
      when(airportRepository.findByIataCodeIgnoreCase("JFK")).thenReturn(Optional.of(origin));
      when(airportRepository.findByIataCodeIgnoreCase("LAX")).thenReturn(Optional.of(destination));
      when(redisTemplate.opsForValue()).thenReturn(valueOps);
      when(valueOps.get(anyString())).thenReturn(null);

      Page<Flight> flightPage = new PageImpl<>(List.of(flight));
      when(flightRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(flightPage);
      when(flightMapper.toResponse(flight)).thenReturn(flightDto);

      FlightSearchResponseDto result = legService.searchLeg(
          "JFK", "LAX", LocalDate.of(2025, 6, 1), request, 0, 20, "PRICE_ASC");

      assertThat(result).isNotNull();
      assertThat(result.getFlights()).hasSize(1);
      assertThat(result.getFlights().get(0).getFlightNumber()).isEqualTo("AA123");
      assertThat(result.getMetadata().getOrigin()).isEqualTo("JFK");
      assertThat(result.getMetadata().getDestination()).isEqualTo("LAX");
      assertThat(result.getMetadata().getTotalResults()).isEqualTo(1);
      assertThat(result.getMetadata().getFromCache()).isFalse();
    }

    @Test
    void shouldThrowWhenOriginAirportNotFound() {
      when(airportRepository.findByIataCodeIgnoreCase("INVALID"))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> legService.searchLeg(
          "INVALID", "LAX", LocalDate.of(2025, 6, 1), request, 0, 20, "PRICE_ASC"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Invalid origin");
    }

    @Test
    void shouldThrowWhenDestinationAirportNotFound() {
      when(airportRepository.findByIataCodeIgnoreCase("JFK")).thenReturn(Optional.of(origin));
      when(airportRepository.findByIataCodeIgnoreCase("INVALID"))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> legService.searchLeg(
          "JFK", "INVALID", LocalDate.of(2025, 6, 1), request, 0, 20, "PRICE_ASC"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Invalid destination");
    }

    @Test
    void shouldReturnCachedResultWhenAvailable() throws Exception {
      when(airportRepository.findByIataCodeIgnoreCase("JFK")).thenReturn(Optional.of(origin));
      when(airportRepository.findByIataCodeIgnoreCase("LAX")).thenReturn(Optional.of(destination));
      when(redisTemplate.opsForValue()).thenReturn(valueOps);

      FlightSearchResponseDto cachedResponse = FlightSearchResponseDto.builder()
          .flights(List.of(flightDto))
          .metadata(FlightSearchResponseDto.SearchMetadata.builder()
              .origin("JFK").destination("LAX").fromCache(true).build())
          .build();
      String cachedJson = objectMapper.writeValueAsString(cachedResponse);
      when(valueOps.get(anyString())).thenReturn(cachedJson);

      FlightSearchResponseDto result = legService.searchLeg(
          "JFK", "LAX", LocalDate.of(2025, 6, 1), request, 0, 20, "PRICE_ASC");

      assertThat(result).isNotNull();
      assertThat(result.getMetadata().getFromCache()).isTrue();
      verify(flightRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldHandleCacheDeserializationFailure() {
      when(airportRepository.findByIataCodeIgnoreCase("JFK")).thenReturn(Optional.of(origin));
      when(airportRepository.findByIataCodeIgnoreCase("LAX")).thenReturn(Optional.of(destination));
      when(redisTemplate.opsForValue()).thenReturn(valueOps);
      when(valueOps.get(anyString())).thenReturn("invalid-json");

      Page<Flight> flightPage = new PageImpl<>(List.of(flight));
      when(flightRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(flightPage);
      when(flightMapper.toResponse(flight)).thenReturn(flightDto);

      FlightSearchResponseDto result = legService.searchLeg(
          "JFK", "LAX", LocalDate.of(2025, 6, 1), request, 0, 20, "PRICE_ASC");

      assertThat(result).isNotNull();
      assertThat(result.getFlights()).hasSize(1);
    }
  }
}