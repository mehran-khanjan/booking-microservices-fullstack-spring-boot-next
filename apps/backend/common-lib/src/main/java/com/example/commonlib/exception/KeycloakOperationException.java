package com.example.commonlib.exception;

/** Exception thrown when a Keycloak admin operation fails unexpectedly. */
public class KeycloakOperationException extends RuntimeException {
  public KeycloakOperationException(String message) {
    super(message);
  }
}
