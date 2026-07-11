package com.example.commonlib.exception;

/**
 * Exception thrown when a Keycloak admin operation fails unexpectedly.
 *
 * <p>Examples include: failure to create a user, update roles, or communicate with the Keycloak
 * admin API.
 */
public class KeycloakOperationException extends RuntimeException {
  public KeycloakOperationException(String message) {
    super(message);
  }
}
