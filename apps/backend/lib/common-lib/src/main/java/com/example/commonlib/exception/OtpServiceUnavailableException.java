package com.example.commonlib.exception;

/**
 * Exception thrown when the OTP delivery service (email or SMS provider) is temporarily unavailable
 * or returns an error.
 *
 * <p>Used to indicate that OTPs cannot be sent at the moment, possibly due to network issues,
 * provider outages, or rate limiting.
 */
public class OtpServiceUnavailableException extends RuntimeException {
  public OtpServiceUnavailableException(String message) {
    super(message);
  }
}
