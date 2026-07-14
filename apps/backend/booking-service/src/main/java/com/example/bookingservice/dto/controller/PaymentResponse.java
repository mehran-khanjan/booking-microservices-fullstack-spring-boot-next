package com.example.bookingservice.dto.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for payment processing operations. Corresponds to the Protobuf message
 * PaymentResponse.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

  /** Unique identifier of the payment transaction. */
  private String transactionId;

  /** Current status of the payment. */
  private PaymentStatus status;

  /** Human-readable message describing the result. */
  private String message;

  /**
   * Timestamp of the response, expressed as milliseconds since epoch. Can be serialised as a
   * numeric value or ISO-8601 string.
   */
  @JsonFormat(shape = JsonFormat.Shape.NUMBER)
  private Long timestamp;

  /**
   * Alternative: use Instant for better type safety. Uncomment the following field and remove the
   * Long timestamp above if you prefer.
   */
  // private Instant timestamp;

  /** Payment status enumeration. */
  public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
  }
}
