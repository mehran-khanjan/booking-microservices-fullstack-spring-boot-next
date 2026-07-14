package com.example.bookingservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Core booking entity representing a flight reservation. Contains references to flights,
 * passengers, and additional services.
 */
@Entity
@Table(
    name = "bookings",
    indexes = {
      @Index(name = "idx_bookings_reference", columnList = "booking_reference"),
      @Index(name = "idx_bookings_user", columnList = "user_id"),
      @Index(name = "idx_bookings_status", columnList = "status")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 6)
  private String bookingReference;

  @Column(nullable = false, length = 100)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  @Builder.Default
  private BookingType bookingType = BookingType.SINGLE;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  @Builder.Default
  private BookingStatus status = BookingStatus.PENDING_PAYMENT;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal totalAmount;

  @Column(length = 3)
  @Builder.Default
  private String currency = "USD";

  @Column(nullable = false)
  private String contactEmail;

  @Column(length = 20)
  private String contactPhone;

  private String paymentTransactionId;

  private LocalDateTime holdExpiresAt;

  private LocalDateTime confirmedAt;

  private LocalDateTime cancelledAt;

  @Column(columnDefinition = "TEXT")
  private String cancellationReason;

  @Column(precision = 10, scale = 2)
  private BigDecimal refundAmount;

  @Version private Integer version;

  @CreationTimestamp
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp private LocalDateTime updatedAt;

  @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<BookingFlight> bookingFlights = new ArrayList<>();

  @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<Passenger> passengers = new ArrayList<>();

  @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<AdditionalService> additionalServices = new ArrayList<>();

  /** Booking type: one-way, round-trip, or multi-city. */
  public enum BookingType {
    SINGLE,
    ROUND_TRIP,
    MULTI_CITY
  }

  /** Booking lifecycle status. */
  public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED
  }

  /**
   * Helper to add a flight to the booking.
   *
   * @param bookingFlight the flight segment to add
   */
  public void addBookingFlight(BookingFlight bookingFlight) {
    bookingFlights.add(bookingFlight);
    bookingFlight.setBooking(this);
  }

  /**
   * Helper to add a passenger to the booking.
   *
   * @param passenger the passenger to add
   */
  public void addPassenger(Passenger passenger) {
    passengers.add(passenger);
    passenger.setBooking(this);
  }

  /**
   * Helper to add an additional service to the booking.
   *
   * @param service the service to add
   */
  public void addAdditionalService(AdditionalService service) {
    additionalServices.add(service);
    service.setBooking(this);
  }
}
