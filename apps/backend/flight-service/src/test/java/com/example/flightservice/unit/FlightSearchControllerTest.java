package com.example.flightservice.unit;

import com.example.flightservice.config.TestSecurityConfig;
import com.example.flightservice.controller.FlightSearchController;
import com.example.flightservice.dto.controller.flightsearch.*;
import com.example.flightservice.service.FlightSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightSearchControllerTest {

  @Mock private FlightSearchService flightSearchService;

  private FlightSearchController controller;

  @BeforeEach
  void setUp() {
    controller = new FlightSearchController(flightSearchService);
  }

  @Test
  void shouldSearchFlightsSuccessfully() {
    FlightSearchRequestDto request = FlightSearchRequestDto.builder()
        .origin("JFK").destination("LAX")
        .departureDate(LocalDate.of(2025, 8, 1))
        .passengers(1)
        .build();

    FlightSearchResponseDto response = FlightSearchResponseDto.builder()
        .flights(List.of())
        .metadata(FlightSearchResponseDto.SearchMetadata.builder()
            .origin("JFK").destination("LAX").totalResults(0L).build())
        .pageInfo(com.example.commonlib.responseenvelope.response.PageResponse.PageInfo.builder()
            .page(0).size(20).totalElements(0).totalPages(0).first(true).last(true).build())
        .build();

    when(flightSearchService.searchFlights(any(FlightSearchRequestDto.class)))
        .thenReturn(response);

    ResponseEntity<com.example.commonlib.responseenvelope.response.ApiResponse<FlightSearchResponseDto>> result =
        controller.searchFlights(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody().isSuccess()).isTrue();
    assertThat(result.getBody().getData()).isSameAs(response);
  }
}