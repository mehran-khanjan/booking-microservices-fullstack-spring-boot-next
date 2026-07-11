package com.example.commonlib.exception;

/**
 * Exception thrown when a Google-issued token (ID token or access token) is invalid, expired, or
 * cannot be verified.
 *
 * <p>This is typically thrown during Google Sign-In or token validation flows.
 */
public class GoogleTokenInvalidException extends RuntimeException {
  public GoogleTokenInvalidException(String message) {
    super(message);
  }
}
