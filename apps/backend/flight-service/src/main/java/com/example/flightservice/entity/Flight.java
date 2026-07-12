package com.example.flightservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Core entity representing a scheduled flight.
 *
 * <p>Contains all operational details: airline, route (origin/destination), timing, seat
 * availability per cabin class, pricing, and status. Optimistic locking ({@code @Version}) is used
 * for concurrency control.
 *
 * <p>Indexes are defined on frequently queried fields:
 *
 * <ul>
 *   <li>{@code idx_flights_route_date} – for searches by origin, destination, and departure date
 *   <li>{@code idx_flights_departure} – for departure date/time filtering
 *   <li>{@code idx_flights_status} – for status‑based queries
 * </ul>
 *
 * @see FlightStatus
 */
@Entity
@Table(
    name = "flights",
    indexes = {
      @Index(
          name = "idx_flights_route_date",
          columnList = "origin_airport_id,destination_airport_id,departure_date"),
      @Index(name = "idx_flights_departure", columnList = "departure_date,departure_time"),
      @Index(name = "idx_flights_status", columnList = "status")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Flight {

  /** Unique identifier. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Public flight number (e.g., "AA123"). */
  @Column(nullable = false, length = 10)
  private String flightNumber;

  /** Airline operating this flight. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "airline_id", nullable = false)
  private Airline airline;

  /** Departure airport. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "origin_airport_id", nullable = false)
  private Airport originAirport;

  /** Destination airport. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "destination_airport_id", nullable = false)
  private Airport destinationAirport;

  /** Aircraft type used for this flight. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "aircraft_type_id", nullable = false)
  private AircraftType aircraftType;

  /** Departure date (local to origin). */
  @Column(nullable = false)
  private LocalDate departureDate;

  /** Departure time (local to origin). */
  @Column(nullable = false)
  private LocalTime departureTime;

  /** Arrival date (local to destination). */
  @Column(nullable = false)
  private LocalDate arrivalDate;

  /** Arrival time (local to destination). */
  @Column(nullable = false)
  private LocalTime arrivalTime;

  /** Total flight duration in minutes. */
  @Column(nullable = false)
  private Integer durationMinutes;

  /** Base price for economy class. */
  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal basePriceEconomy;

  /** Base price for premium economy class (nullable if not offered). */
  @Column(precision = 10, scale = 2)
  private BigDecimal basePricePremiumEconomy;

  /** Base price for business class (nullable). */
  @Column(precision = 10, scale = 2)
  private BigDecimal basePriceBusiness;

  /** Base price for first class (nullable). */
  @Column(precision = 10, scale = 2)
  private BigDecimal basePriceFirstClass;

  /** Currency code (e.g., "USD"). */
  @Column(length = 3)
  @Builder.Default
  private String currency = "USD";

  /** Total seats available on the aircraft. */
  @Column(nullable = false)
  private Integer totalSeats;

  /** Total remaining available seats (sum of all cabins). */
  @Column(nullable = false)
  private Integer availableSeats;

  /** Economy seats still available. */
  @Column(nullable = false)
  private Integer economyAvailable;

  /** Premium economy seats still available (default 0). */
  @Builder.Default private Integer premiumEconomyAvailable = 0;

  /** Business seats still available (default 0). */
  @Builder.Default private Integer businessAvailable = 0;

  /** First class seats still available (default 0). */
  @Builder.Default private Integer firstClassAvailable = 0;

  /** Current flight status (e.g., SCHEDULED, DELAYED). */
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  @Builder.Default
  private FlightStatus status = FlightStatus.SCHEDULED;

  /** Standard checked baggage allowance in kg. */
  @Builder.Default private Integer baggageAllowanceKg = 23;

  /** Standard cabin baggage allowance in kg. */
  @Builder.Default private Integer cabinBaggageAllowanceKg = 7;

  /** Textual description of cancellation policy. */
  @Column(columnDefinition = "TEXT")
  private String cancellationPolicy;

  /** Optimistic locking version field. */
  @Version private Integer version;

  /** Creation timestamp. */
  @CreationTimestamp
  @Column(updatable = false)
  private LocalDateTime createdAt;

  /** Last update timestamp. */
  @UpdateTimestamp private LocalDateTime updatedAt;

  /** Enumeration of possible flight statuses. */
  public enum FlightStatus {
    SCHEDULED,
    DELAYED,
    CANCELLED,
    DEPARTED,
    ARRIVED,
    FULL
  }
}
