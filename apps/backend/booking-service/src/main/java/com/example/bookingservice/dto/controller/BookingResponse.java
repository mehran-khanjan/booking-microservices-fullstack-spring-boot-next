package com.example.bookingservice.dto.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for a booking, containing all flight, passenger, and service details. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {

  private Long id;
  private String bookingReference;
  private String status;
  private String bookingType;

  private BigDecimal totalAmount;
  private String currency;

  private String contactEmail;
  private String contactPhone;

  private List<FlightInfo> flights;
  private List<PassengerInfo> passengers;
  private List<ServiceInfo> additionalServices;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime holdExpiresAt;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime confirmedAt;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime cancelledAt;

  private String cancellationReason;
  private BigDecimal refundAmount;

  private String paymentTransactionId;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private LocalDateTime createdAt;

  /** Flight information within the booking. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class FlightInfo {
    private Long flightId;
    private String flightNumber;
    private String airline;
    private String origin;
    private String destination;
    private String departureTime;
    private String arrivalTime;
    private String cabinClass;
    private BigDecimal price;
    private Integer segmentOrder;
  }

  /** Passenger information within the booking. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class PassengerInfo {
    private Long id;
    private String title;
    private String firstName;
    private String lastName;
    private String dateOfBirth;
    private String nationality;
    private String passportNumber;
    private String mealPreference;
  }

  /** Additional service information (e.g., extra baggage, meals). */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ServiceInfo {
    private String serviceType;
    private String description;
    private BigDecimal price;
  }
}
