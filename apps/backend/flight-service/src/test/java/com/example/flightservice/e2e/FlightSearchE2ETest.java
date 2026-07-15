package com.example.flightservice.e2e;

import com.example.flightservice.config.TestSecurityConfig;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchRequestDto;
import com.example.flightservice.service.FlightSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class FlightSearchE2ETest {

  @LocalServerPort private int port;

  @MockitoBean private FlightSearchService flightSearchService;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = RestClient.create("http://localhost:" + port);
  }

  @Test
  void shouldReturn401WhenNoAuthToken() throws IOException {
    FlightSearchRequestDto request = FlightSearchRequestDto.builder()
        .origin("JFK").destination("LAX")
        .departureDate(LocalDate.of(2027, 8, 1))
        .passengers(1)
        .build();

    var response = restClient.post()
        .uri("/api/v1/flights/search")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange((req, res) -> res);

    assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldReturn200ForValidRequest() throws IOException {
    FlightSearchRequestDto request = FlightSearchRequestDto.builder()
        .origin("JFK").destination("LAX")
        .departureDate(LocalDate.of(2027, 8, 1))
        .passengers(1)
        .build();

    var mockData = com.example.flightservice.dto.controller.flightsearch.FlightSearchResponseDto.builder()
        .flights(java.util.List.of())
        .metadata(com.example.flightservice.dto.controller.flightsearch.FlightSearchResponseDto.SearchMetadata.builder()
            .origin("JFK").destination("LAX").totalResults(0L).build())
        .pageInfo(com.example.commonlib.responseenvelope.response.PageResponse.PageInfo.builder()
            .page(0).size(20).totalElements(0).totalPages(0).first(true).last(true).build())
        .build();

    when(flightSearchService.searchFlights(any(FlightSearchRequestDto.class)))
        .thenReturn(mockData);

    var response = restClient.post()
        .uri("/api/v1/flights/search")
        .header("Authorization", "Bearer " + TestSecurityConfig.VALID_TOKEN)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange((req, res) -> res);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldVerifyServiceBeansAreLoaded() {
    assertThat(flightSearchService).isNotNull();
    assertThat(restClient).isNotNull();
  }
}