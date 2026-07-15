package com.example.flightservice.unit;

import com.example.flightservice.constant.ErrorCodes;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchRequestDto;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchResponseDto;
import com.example.flightservice.exception.BusinessException;
import com.example.flightservice.service.FlightSearchLegService;
import com.example.flightservice.service.FlightSearchService;
import com.example.flightservice.util.ContextPropagatingSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightSearchServiceTest {

  @Mock private FlightSearchLegService legService;

  private FlightSearchService searchService;
  private Executor directExecutor;

  @BeforeEach
  void setUp() {
    // Use a direct executor that runs tasks synchronously for testing
    directExecutor = Runnable::run;
    searchService = new FlightSearchService(legService, directExecutor);
  }

  @Nested
  class OneWaySearch {

    @Test
    void shouldReturnOneWayResults() {
      FlightSearchRequestDto request = FlightSearchRequestDto.builder()
          .origin("JFK").destination("LAX")
          .departureDate(LocalDate.of(2025, 6, 1))
          .passengers(1).build();

      FlightSearchResponseDto legResult = FlightSearchResponseDto.builder()
          .flights(List.of())
          .metadata(FlightSearchResponseDto.SearchMetadata.builder()
              .origin("JFK").destination("LAX").totalResults(0L).build())
          .pageInfo(com.example.commonlib.responseenvelope.response.PageResponse.PageInfo.builder()
              .page(0).size(20).totalElements(0).totalPages(0).first(true).last(true).build())
          .build();

      when(legService.searchLeg(eq("JFK"), eq("LAX"), eq(LocalDate.of(2025, 6, 1)),
          same(request), eq(0), eq(20), anyString()))
          .thenReturn(legResult);

      FlightSearchResponseDto result = searchService.searchFlights(request);

      assertThat(result).isSameAs(legResult);
    }
  }

  @Nested
  class RoundTripSearch {

    @Test
    void shouldReturnRoundTripResults() {
      FlightSearchRequestDto request = FlightSearchRequestDto.builder()
          .origin("JFK").destination("LAX")
          .departureDate(LocalDate.of(2025, 6, 1))
          .returnDate(LocalDate.of(2025, 6, 8))
          .passengers(1).build();

      FlightSearchResponseDto outbound = FlightSearchResponseDto.builder()
          .flights(List.of())
          .metadata(FlightSearchResponseDto.SearchMetadata.builder()
              .origin("JFK").destination("LAX").totalResults(2L).build())
          .pageInfo(com.example.commonlib.responseenvelope.response.PageResponse.PageInfo.builder()
              .page(0).size(20).totalElements(2).totalPages(1).first(true).last(true).build())
          .build();

      FlightSearchResponseDto returnLeg = FlightSearchResponseDto.builder()
          .returnFlights(List.of())
          .metadata(FlightSearchResponseDto.SearchMetadata.builder()
              .origin("LAX").destination("JFK").totalResults(1L).build())
          .returnPageInfo(com.example.commonlib.responseenvelope.response.PageResponse.PageInfo.builder()
              .page(0).size(20).totalElements(1).totalPages(1).first(true).last(true).build())
          .build();

      when(legService.searchLeg(eq("JFK"), eq("LAX"), eq(LocalDate.of(2025, 6, 1)),
          same(request), eq(0), eq(20), eq("PRICE_ASC")))
          .thenReturn(outbound);
      when(legService.searchLeg(eq("LAX"), eq("JFK"), eq(LocalDate.of(2025, 6, 8)),
          same(request), eq(0), eq(20), eq("PRICE_ASC")))
          .thenReturn(returnLeg);

      FlightSearchResponseDto result = searchService.searchFlights(request);

      assertThat(result).isNotNull();
      assertThat(result.getFlights()).isEmpty();
      assertThat(result.getMetadata().getOrigin()).isEqualTo("JFK");
      assertThat(result.getMetadata().getDestination()).isEqualTo("LAX");
      assertThat(result.getMetadata().getReturnDate()).isEqualTo("2025-06-08");
      assertThat(result.getMetadata().getTotalResults()).isEqualTo(3L);
    }

    @Test
    void shouldThrowWhenReturnDateNotAfterDeparture() {
      FlightSearchRequestDto request = FlightSearchRequestDto.builder()
          .origin("JFK").destination("LAX")
          .departureDate(LocalDate.of(2025, 6, 8))
          .returnDate(LocalDate.of(2025, 6, 1))
          .passengers(1).build();

      assertThatThrownBy(() -> searchService.searchFlights(request))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Return date must be after departure date");
    }

    @Test
    void shouldThrowWhenReturnDateEqualsDeparture() {
      FlightSearchRequestDto request = FlightSearchRequestDto.builder()
          .origin("JFK").destination("LAX")
          .departureDate(LocalDate.of(2025, 6, 1))
          .returnDate(LocalDate.of(2025, 6, 1))
          .passengers(1).build();

      assertThatThrownBy(() -> searchService.searchFlights(request))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Return date must be after departure date");
    }

    @Test
    void shouldUnwrapBusinessExceptionFromAsyncTask() {
      FlightSearchRequestDto request = FlightSearchRequestDto.builder()
          .origin("JFK").destination("LAX")
          .departureDate(LocalDate.of(2025, 6, 1))
          .returnDate(LocalDate.of(2025, 6, 8))
          .passengers(1).build();

      when(legService.searchLeg(eq("JFK"), eq("LAX"), eq(LocalDate.of(2025, 6, 1)),
          same(request), eq(0), eq(20), eq("PRICE_ASC")))
          .thenThrow(new BusinessException("Leg search failed", ErrorCodes.INVALID_SEARCH_PARAMS));

      assertThatThrownBy(() -> searchService.searchFlights(request))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Leg search failed");
    }
  }
}