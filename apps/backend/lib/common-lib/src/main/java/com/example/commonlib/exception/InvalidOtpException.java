package com.example.commonlib.exception;

/**
 * Exception thrown when an OTP (One‑Time Password) provided by the user is invalid, expired, or
 * does not match the expected value.
 *
 * <p>Used during OTP verification flows (e.g., email or SMS verification).
 */
public class InvalidOtpException extends RuntimeException {
  public InvalidOtpException(String message) {
    super(message);
  }
}
