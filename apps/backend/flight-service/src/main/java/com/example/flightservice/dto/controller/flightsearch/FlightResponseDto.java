package com.example.flightservice.dto.controller.flightsearch;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a full flight response returned to the client.
 *
 * <p>Contains detailed information about a single flight, including airline, airports, timing,
 * pricing, seat availability, and baggage policies. This is used as the main payload for search
 * results and booking confirmations.
 *
 * @see FlightSearchResponseDto
 * @see FlightSearchRequestDto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightResponseDto {

  /** Unique identifier of the flight. */
  private Long id;

  /** Public flight number (e.g., "AA123"). */
  private String flightNumber;

  /** Airline operating the flight. */
  private AirlineInfo airline;

  /** Departure airport. */
  private AirportInfo origin;

  /** Destination airport. */
  private AirportInfo destination;

  /** Departure date (local to origin). */
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate departureDate;

  /** Departure time (local to origin). */
  @JsonFormat(pattern = "HH:mm")
  private LocalTime departureTime;

  /** Arrival date (local to destination). */
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate arrivalDate;

  /** Arrival time (local to destination). */
  @JsonFormat(pattern = "HH:mm")
  private LocalTime arrivalTime;

  /** Total flight duration in minutes. */
  private Integer durationMinutes;

  /** Human‑readable duration, e.g. "2h 30m". */
  private String durationFormatted;

  /** Pricing information per cabin class. */
  private PricingInfo pricing;

  /** Type of aircraft. */
  private String aircraftType;

  /** Manufacturer of the aircraft (e.g., "Boeing"). */
  private String aircraftManufacturer;

  /** Total number of seats on the aircraft. */
  private Integer totalSeats;

  /** Number of seats still available across all cabins. */
  private Integer availableSeats;

  /** Detailed seat availability per cabin class. */
  private SeatAvailability seatAvailability;

  /** Current status of the flight (e.g., SCHEDULED, DELAYED). */
  private String status;

  /** Standard checked baggage allowance in kilograms. */
  private Integer baggageAllowanceKg;

  /** Standard cabin baggage allowance in kilograms. */
  private Integer cabinBaggageAllowanceKg;

  /** Brief description of the cancellation policy. */
  private String cancellationPolicy;

  /** Nested DTO for airline information. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AirlineInfo {
    private Long id;
    private String code;
    private String name;
    private String country;
    private String logoUrl;
  }

  /** Nested DTO for airport information. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AirportInfo {
    private Long id;
    private String iataCode;
    private String name;
    private String city;
    private String country;
    private String timezone;
  }

  /** Nested DTO holding pricing per cabin class. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class PricingInfo {
    private BigDecimal economy;
    private BigDecimal premiumEconomy;
    private BigDecimal business;
    private BigDecimal firstClass;
    private String currency;
  }

  /** Nested DTO for seat availability per cabin class. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class SeatAvailability {
    private Integer economy;
    private Integer premiumEconomy;
    private Integer business;
    private Integer firstClass;
  }
}
