package com.example.bookingservice.servcie.payment;

import lombok.Getter;

/**
 * Gateway-agnostic outcome of a charge attempt. Never carries raw card/gateway internals beyond a
 * provider-issued transaction/capture ID and a human-readable status/failure reason - safe to log
 * and to persist on the booking.
 */
@Getter
public class PaymentGatewayResult {

  private final boolean success;
  private final String transactionId;
  private final String providerStatus;
  private final String failureReason;

  private PaymentGatewayResult(
      boolean success, String transactionId, String providerStatus, String failureReason) {
    this.success = success;
    this.transactionId = transactionId;
    this.providerStatus = providerStatus;
    this.failureReason = failureReason;
  }

  /** Creates a successful result with the gateway's transaction ID and status. */
  public static PaymentGatewayResult success(String transactionId, String providerStatus) {
    return new PaymentGatewayResult(true, transactionId, providerStatus, null);
  }

  /** Creates a failure result with a human-readable reason. */
  public static PaymentGatewayResult failure(String failureReason) {
    return new PaymentGatewayResult(false, null, null, failureReason);
  }
}
