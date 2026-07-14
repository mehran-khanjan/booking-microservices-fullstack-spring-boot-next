package com.example.bookingservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity representing an aircraft type (model) with seat capacity per cabin.
 *
 * <p>Used as a reference for flights; defines total seats and how they are distributed among
 * economy, premium economy, business, and first class. This is a static catalog entity.
 */
@Entity
@Table(name = "aircraft_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AircraftType {

  /** Unique identifier. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Aircraft model name (e.g., "Boeing 737-800"). */
  @Column(nullable = false, unique = true, length = 100)
  private String model;

  /** Manufacturer name (e.g., "Boeing"). */
  @Column(length = 100)
  private String manufacturer;

  /** Total number of seats on the aircraft. */
  @Column(nullable = false)
  private Integer totalSeats;

  /** Number of economy seats. */
  @Column(nullable = false)
  private Integer economySeats;

  /** Number of premium economy seats (default 0). */
  @Builder.Default private Integer premiumEconomySeats = 0;

  /** Number of business seats (default 0). */
  @Builder.Default private Integer businessSeats = 0;

  /** Number of first class seats (default 0). */
  @Builder.Default private Integer firstClassSeats = 0;

  /** Timestamp when this record was created. */
  @CreationTimestamp
  @Column(updatable = false)
  private LocalDateTime createdAt;
}
