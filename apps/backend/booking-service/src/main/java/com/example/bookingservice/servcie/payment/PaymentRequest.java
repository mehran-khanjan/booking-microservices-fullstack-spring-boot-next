package com.example.bookingservice.servcie.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal payment request used by the OrderService to trigger a gateway charge. Contains booking
 * reference, amount, currency, and gateway-specific tokens.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

  @NotBlank(message = "Booking reference is required")
  private String bookingReference;

  @NotBlank(message = "Payment method is required")
  private String paymentMethod; // CREDIT_CARD, DEBIT_CARD, STRIPE, PAYPAL

  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
  private BigDecimal amount;

  @NotBlank(message = "Currency is required")
  private String currency;

  private String
      paymentToken; // From frontend payment gateway (Stripe PaymentMethod ID / PayPal Order ID)

  private String idempotencyKey;
}
