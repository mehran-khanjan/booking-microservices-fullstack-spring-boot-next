package com.example.bookingservice.servcie.payment;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway-agnostic charge request. {@code OrderService} builds one of these from the booking and
 * hands it to whichever {@link PaymentGatewayService} matches the requested payment method.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentChargeRequest {

  /** Booking reference, used for gateway-side metadata/description, not for auth. */
  private String bookingReference;

  /** Exact amount to charge, in major currency units (e.g. 129.99 for $129.99). */
  private BigDecimal amount;

  /** ISO 4217 currency code (e.g. "USD"). */
  private String currency;

  /**
   * Provider-specific payment reference created client-side: - Stripe: a PaymentMethod ID (e.g.
   * "pm_1234...") - PayPal: an Order ID created via the PayPal JS SDK on the frontend
   */
  private String paymentToken;

  /**
   * Idempotency key for this charge attempt. Must be stable across retries of the *same* logical
   * payment attempt so the gateway can safely dedupe network retries, but different across
   * genuinely distinct attempts (e.g. a user re-trying after a declined card).
   */
  private String idempotencyKey;
}
