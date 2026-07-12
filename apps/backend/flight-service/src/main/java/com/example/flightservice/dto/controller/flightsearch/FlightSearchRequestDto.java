package com.example.flightservice.dto.controller.flightsearch;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for flight search operations.
 *
 * <p>Encapsulates all possible search criteria, including mandatory fields (origin, destination,
 * departure date) and optional filters (price range, cabin class, sorting, pagination). All fields
 * are validated using JSR‑380 annotations.
 *
 * @see FlightSearchResponseDto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightSearchRequestDto {

  /** IATA code of the departure airport (3 letters, mandatory). */
  @NotBlank(message = "Origin airport code is required")
  @Size(min = 3, max = 3, message = "Origin must be a valid 3-letter IATA code")
  private String origin;

  /** IATA code of the destination airport (3 letters, mandatory). */
  @NotBlank(message = "Destination airport code is required")
  @Size(min = 3, max = 3, message = "Destination must be a valid 3-letter IATA code")
  private String destination;

  /** Departure date (must be in the future). */
  @NotNull(message = "Departure date is required")
  @Future(message = "Departure date must be in the future")
  private LocalDate departureDate;

  /** Return date for round‑trip searches (must be in the future, optional). */
  @Future(message = "Return date must be in the future")
  private LocalDate returnDate;

  /** Number of passengers (1‑9). */
  @Min(value = 1, message = "At least 1 passenger is required")
  @Max(value = 9, message = "Maximum 9 passengers allowed per booking")
  @Builder.Default
  private Integer passengers = 1;

  /** Preferred cabin class: ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST_CLASS. */
  private String cabinClass;

  // Advanced filters

  /** Minimum price filter (optional). */
  private Double minPrice;

  /** Maximum price filter (optional). */
  private Double maxPrice;

  /** Filter by specific airline (code or name). */
  private String airline;

  /** Maximum number of stops (0 for direct). */
  private Integer maxStops;

  /** Preferred departure time range: MORNING, AFTERNOON, EVENING, NIGHT. */
  private String departureTimeRange;

  /** Preferred arrival time range. */
  private String arrivalTimeRange;

  /** Filter by aircraft type (e.g., "Boeing 737"). */
  private String aircraftType;

  /** Whether to allow flexible dates (adjacent days). */
  @Builder.Default private Boolean flexibleDates = false;

  // Sorting

  /** Sort order: PRICE_ASC, PRICE_DESC, DURATION, DEPARTURE_TIME, ARRIVAL_TIME. */
  @Builder.Default private String sortBy = "PRICE_ASC";

  // Pagination

  /** Page number (zero‑based). */
  @Min(value = 0)
  @Builder.Default
  private Integer page = 0;

  /** Page size (1‑100). */
  @Min(value = 1)
  @Max(value = 100)
  @Builder.Default
  private Integer size = 20;
}
