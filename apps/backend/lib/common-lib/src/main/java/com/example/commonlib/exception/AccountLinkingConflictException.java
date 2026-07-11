package com.example.commonlib.exception;

/**
 * Exception thrown when an account linking operation conflicts with existing associations.
 *
 * <p>For example, when trying to link a social login (Google, Facebook) to a user account that is
 * already linked to a different provider, or when a user tries to link an account that is already
 * associated with another user.
 */
public class AccountLinkingConflictException extends RuntimeException {
  public AccountLinkingConflictException(String message) {
    super(message);
  }
}
