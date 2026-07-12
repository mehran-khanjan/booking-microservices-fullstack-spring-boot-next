package com.example.flightservice.dto.controller.flightsearch;

import com.example.commonlib.responseenvelope.response.PageResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for flight search operations.
 *
 * <p>Contains the list of outbound flights (and optionally return flights for round‑trip searches)
 * along with metadata and pagination information. Each flight is represented as a {@link
 * FlightResponseDto}.
 *
 * @see FlightSearchRequestDto
 * @see FlightResponseDto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightSearchResponseDto {

  /** List of outbound flights (always present). */
  private List<FlightResponseDto> flights;

  /** List of return flights (only for round‑trip). */
  private List<FlightResponseDto> returnFlights;

  /** Metadata for the outbound search. */
  private SearchMetadata metadata;

  /** Metadata for the return search (optional). */
  private SearchMetadata returnMetadata;

  /** Pagination info for the outbound results. */
  private PageResponse.PageInfo pageInfo;

  /** Pagination info for the return results (optional). */
  private PageResponse.PageInfo returnPageInfo;

  /** Nested class holding search‑specific metadata. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class SearchMetadata {
    private String origin;
    private String destination;
    private String departureDate;
    private String returnDate; // will be set for round‑trip
    private Integer passengers;
    private String cabinClass;
    private Long totalResults;
    private String searchId;
    private Boolean fromCache;
  }
}
