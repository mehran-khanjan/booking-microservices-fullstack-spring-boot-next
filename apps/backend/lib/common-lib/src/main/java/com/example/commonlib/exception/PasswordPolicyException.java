package com.example.commonlib.exception;

/**
 * Exception thrown when a user's password does not meet the defined security policy requirements
 * (e.g., minimum length, complexity, or disallowed patterns).
 *
 * <p>Used during password creation or reset flows to enforce strong passwords.
 */
public class PasswordPolicyException extends RuntimeException {
  public PasswordPolicyException(String message) {
    super(message);
  }
}
