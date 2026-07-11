package com.example.commonlib.exception;

/**
 * Exception thrown when attempting to register a user that already exists.
 *
 * <p>This typically occurs when a sign‑up request uses an email or username that is already
 * associated with an active account.
 */
public class UserAlreadyExistsException extends RuntimeException {
  public UserAlreadyExistsException(String message) {
    super(message);
  }
}
