package com.example.bookingservice.servcie.payment;

/**
 * Implemented by each concrete payment gateway integration. {@link PaymentGatewayFactory} selects
 * an implementation by {@link #getProviderName()} based on the payment method the client chose.
 */
public interface PaymentGatewayService {

  /** Unique provider identifier, e.g. "STRIPE" or "PAYPAL". */
  String getProviderName();

  /**
   * Attempts to charge the given request. Implementations must never throw for an ordinary
   * decline/failure - they should return a {@link PaymentGatewayResult#failure(String)} so the
   * caller can handle it as a normal business outcome. Exceptions are reserved for genuine
   * infrastructure failures (the gateway is unreachable, credentials are invalid, etc).
   */
  PaymentGatewayResult charge(PaymentChargeRequest request);
}
