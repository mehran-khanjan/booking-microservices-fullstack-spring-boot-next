package com.example.bookingservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing an airline.
 *
 * <p>Contains basic airline information such as IATA code, name, country, and logo URL. Used by
 * flights to indicate the operating carrier.
 */
@Entity
@Table(name = "airlines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Airline {

  /** Unique identifier. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** IATA airline code (2‑ or 3‑letter, unique). */
  @Column(nullable = false, unique = true, length = 3)
  private String code;

  /** Full name of the airline. */
  @Column(nullable = false, length = 100)
  private String name;

  /** Country of registration. */
  @Column(length = 100)
  private String country;

  /** URL to the airline's logo image. */
  @Column(length = 255)
  private String logoUrl;

  /** Whether the airline is currently active (for soft delete). */
  @Column(nullable = false)
  @Builder.Default
  private Boolean active = true;

  /** Creation timestamp. */
  @CreationTimestamp
  @Column(updatable = false)
  private LocalDateTime createdAt;

  /** Last update timestamp. */
  @UpdateTimestamp private LocalDateTime updatedAt;
}
