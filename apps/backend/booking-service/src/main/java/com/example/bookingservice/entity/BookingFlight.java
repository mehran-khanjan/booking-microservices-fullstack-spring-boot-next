package com.example.bookingservice.entity;

import com.example.bookingservice.enums.CabinClass;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.*;

/**
 * Entity representing a specific flight segment within a booking. Denormalizes flight data to
 * preserve snapshot at booking time.
 */
@Entity
@Table(name = "booking_flights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingFlight {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id", nullable = false)
  private Booking booking;

  // ----- denormalized flight data -----
  @Column(name = "flight_id", nullable = false)
  private Long flightId;

  @Column(name = "flight_number", nullable = false, length = 10)
  private String flightNumber;

  @Column(name = "airline_name", nullable = false, length = 100)
  private String airlineName;

  @Column(name = "origin_airport_code", nullable = false, length = 3)
  private String originAirportCode;

  @Column(name = "destination_airport_code", nullable = false, length = 3)
  private String destinationAirportCode;

  @Column(name = "departure_date", nullable = false)
  private LocalDate departureDate;

  @Column(name = "departure_time", nullable = false)
  private LocalTime departureTime;

  @Column(name = "arrival_date", nullable = false)
  private LocalDate arrivalDate;

  @Column(name = "arrival_time", nullable = false)
  private LocalTime arrivalTime;

  // ----- booking-specific -----
  @Column(nullable = false)
  private Integer segmentOrder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CabinClass cabinClass;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal basePrice;

  @Column(precision = 10, scale = 2)
  @Builder.Default
  private BigDecimal taxesFees = BigDecimal.ZERO;

  /**
   * @return the total price including taxes and fees
   */
  public BigDecimal getTotalPrice() {
    return basePrice.add(taxesFees);
  }
}
