package com.example.flightservice.controller;

import com.example.commonlib.idempotency.Idempotent;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import com.example.commonlib.route.ApiRoutes;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchRequestDto;
import com.example.flightservice.dto.controller.flightsearch.FlightSearchResponseDto;
import com.example.flightservice.service.FlightSearchService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for flight search operations.
 *
 * <p>Exposes endpoints to search for available flights based on origin, destination, and departure
 * date. The controller applies:
 *
 * <ul>
 *   <li><b>Role‑based security</b> – only users with the {@code ROLE_USER} authority may call these
 *       endpoints
 *   <li><b>Rate limiting</b> – using Resilience4j to protect downstream systems
 *   <li><b>Idempotency</b> – with a TTL of 1 hour to safely retry the same search
 * </ul>
 *
 * @see FlightSearchService
 * @see FlightSearchRequestDto
 * @see FlightSearchResponseDto
 */
@RestController
@AllArgsConstructor
@Slf4j
public class FlightSearchController {

  private final FlightSearchService flightSearchService;

  /**
   * Searches for flights based on the provided criteria.
   *
   * <p>This method is idempotent for a given request payload (idempotency key managed via the
   * {@code @Idempotent} annotation). It returns a standard {@link ApiResponse} envelope containing
   * the search results.
   *
   * @param request the search criteria (origin, destination, date, etc.) – must be valid
   * @return a {@link ResponseEntity} with HTTP 200 OK and the flight search results
   * @see ApiRoutes.Flight#FLIGHT_SEARCH
   * @see ApiRoutes.Flight#FLIGHT_SEARCH_TS (trailing‑slash variant)
   */
  @PostMapping(path = {ApiRoutes.Flight.FLIGHT_SEARCH, ApiRoutes.Flight.FLIGHT_SEARCH_TS})
  @PreAuthorize("hasRole(@role.user())")
  @RateLimiter(name = "globalRateLimiter")
  @Idempotent(ttlSeconds = 3600)
  public ResponseEntity<ApiResponse<FlightSearchResponseDto>> searchFlights(
      @Valid @RequestBody FlightSearchRequestDto request) {

    log.info(
        "Flight search request: origin={}, destination={}, date={}",
        request.getOrigin(),
        request.getDestination(),
        request.getDepartureDate());

    FlightSearchResponseDto response = flightSearchService.searchFlights(request);

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK, "Flights retrieved successfully", response));
  }
}
