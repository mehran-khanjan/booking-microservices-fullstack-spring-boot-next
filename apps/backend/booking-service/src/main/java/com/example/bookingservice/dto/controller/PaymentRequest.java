package com.example.bookingservice.dto.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for processing a payment. Contains booking reference, payment method, amount, and
 * gateway tokens.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

  @NotBlank(message = "Booking reference is required")
  private String bookingReference;

  @NotBlank(message = "Payment method is required")
  private String paymentMethod; // CREDIT_CARD, PAYPAL, STRIPE, etc.

  @NotNull(message = "Amount is required")
  private BigDecimal amount;

  @NotBlank(message = "Currency is required")
  private String currency;

  private String paymentToken; // From frontend payment gateway

  private String idempotencyKey;
}
