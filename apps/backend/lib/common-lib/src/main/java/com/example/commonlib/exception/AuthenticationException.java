package com.example.commonlib.exception;

/**
 * Exception thrown when authentication fails (e.g., invalid credentials, missing token, or
 * unsupported authentication method).
 *
 * <p>This is a generic authentication failure that can be used across different authentication
 * flows.
 */
public class AuthenticationException extends RuntimeException {
  public AuthenticationException(String message) {
    super(message);
  }
}
