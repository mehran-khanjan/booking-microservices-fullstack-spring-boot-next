package com.example.bookingservice.dto.controller;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for cancelling a booking, either fully or partially. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelBookingRequest {

  @NotBlank(message = "Booking reference is required")
  private String bookingReference;

  private String cancellationReason;

  private List<Long> passengerIds; // For partial cancellation

  @Builder.Default private Boolean fullCancellation = true;
}
