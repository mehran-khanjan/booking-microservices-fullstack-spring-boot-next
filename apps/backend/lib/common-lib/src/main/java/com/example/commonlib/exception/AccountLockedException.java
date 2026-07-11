package com.example.commonlib.exception;

/**
 * Exception thrown when a user account is locked due to security reasons.
 *
 * <p>Causes may include too many failed login attempts, administrative action, or detection of
 * suspicious activity.
 */
public class AccountLockedException extends RuntimeException {
  public AccountLockedException(String message) {
    super(message);
  }
}
