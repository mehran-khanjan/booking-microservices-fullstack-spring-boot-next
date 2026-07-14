package com.example.bookingservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/** Entity representing an additional service attached to a booking (e.g., extra baggage, meal). */
@Entity
@Table(name = "additional_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdditionalService {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id", nullable = false)
  private Booking booking;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "passenger_id")
  private Passenger passenger;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private ServiceType serviceType;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(length = 3)
  @Builder.Default
  private String currency = "USD";

  @CreationTimestamp
  @Column(updatable = false)
  private LocalDateTime createdAt;

  /** Supported service types. */
  public enum ServiceType {
    EXTRA_BAGGAGE,
    MEAL,
    TRAVEL_INSURANCE,
    PRIORITY_BOARDING,
    LOUNGE_ACCESS,
    SEAT_SELECTION
  }
}
