package com.example.commonlib.exception;

/**
 * Exception thrown when a user attempts to perform an action that requires email or phone
 * verification, but the account is not yet verified.
 *
 * <p>This is typically used to enforce verification before allowing certain operations.
 */
public class AccountNotVerifiedException extends RuntimeException {
  public AccountNotVerifiedException(String message) {
    super(message);
  }
}
