package com.example.flightservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing an airport.
 *
 * <p>Stores IATA/ICAO codes, location details (city, country, timezone), and geographic
 * coordinates. Used as origin/destination for flights.
 */
@Entity
@Table(name = "airports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Airport {

  /** Unique identifier. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** IATA airport code (3 letters, unique). */
  @Column(nullable = false, unique = true, length = 3)
  private String iataCode;

  /** ICAO airport code (4 letters, optional). */
  @Column(length = 4)
  private String icaoCode;

  /** Full name of the airport. */
  @Column(nullable = false)
  private String name;

  /** City where the airport is located. */
  @Column(nullable = false, length = 100)
  private String city;

  /** Country where the airport is located. */
  @Column(nullable = false, length = 100)
  private String country;

  /** IANA timezone name (e.g., "America/New_York"). */
  @Column(length = 50)
  private String timezone;

  /** Latitude coordinate (decimal degrees). */
  @Column(precision = 10, scale = 6)
  private BigDecimal latitude;

  /** Longitude coordinate (decimal degrees). */
  @Column(precision = 10, scale = 6)
  private BigDecimal longitude;

  /** Creation timestamp. */
  @CreationTimestamp
  @Column(updatable = false)
  private LocalDateTime createdAt;

  /** Last update timestamp. */
  @UpdateTimestamp private LocalDateTime updatedAt;
}
