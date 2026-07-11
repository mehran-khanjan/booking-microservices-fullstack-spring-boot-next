package com.example.commonlib.exception;

/**
 * Exception thrown when a user exceeds the allowed number of OTP verification attempts.
 *
 * <p>Used to prevent brute‑force attacks and to enforce rate‑limiting on OTP validation.
 */
public class TooManyOtpAttemptsException extends RuntimeException {
  public TooManyOtpAttemptsException(String message) {
    super(message);
  }
}
