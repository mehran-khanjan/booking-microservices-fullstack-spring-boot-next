package com.example.commonlib.exception;

/**
 * Exception thrown when a Google account's email address is not verified.
 *
 * <p>This occurs during Google Sign‑In when the {@code email_verified} claim is false, indicating
 * that the user has not confirmed their email with Google.
 */
public class EmailNotVerifiedByGoogleException extends RuntimeException {
  public EmailNotVerifiedByGoogleException(String message) {
    super(message);
  }
}
