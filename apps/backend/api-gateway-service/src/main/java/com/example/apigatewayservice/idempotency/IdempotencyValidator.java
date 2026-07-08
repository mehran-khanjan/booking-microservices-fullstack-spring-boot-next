package com.example.apigatewayservice.idempotency;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates idempotency requirements for HTTP requests.
 *
 * <p>Determines which methods require idempotency checks (all except GET) and validates the {@code
 * Idempotency-Key} header as a strict UUID v4.
 */
@Component
public class IdempotencyValidator {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyValidator.class);

  // All HTTP methods except GET require idempotency key validation
  private static final Set<String> CHECKABLE_METHODS =
      Set.of("POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT");

  // UUID v4 pattern (strict validation)
  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private final IdempotencyProperties properties;

  public IdempotencyValidator(IdempotencyProperties properties) {
    this.properties = properties;
  }

  /**
   * Determines if the given HTTP method requires idempotency key validation.
   *
   * @param method HTTP method (e.g., POST, GET)
   * @return {@code true} if the method requires a valid Idempotency-Key
   */
  public boolean requiresIdempotencyCheck(String method) {
    if (method == null || method.isBlank()) {
      return false;
    }

    boolean requires = CHECKABLE_METHODS.contains(method.toUpperCase());

    if (log.isTraceEnabled()) {
      log.trace("Method {} requires idempotency check: {}", method, requires);
    }

    return requires;
  }

  /**
   * Validates that the provided idempotency key is a valid UUID format.
   *
   * @param idempotencyKey the key from the header
   * @return {@code true} if the key is a valid UUID
   */
  public boolean isValidUUID(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return false;
    }

    // Check length first (performance optimization)
    if (idempotencyKey.length() > properties.getMaxLength()) {
      log.warn(
          "Idempotency-Key exceeds max length {}: {}",
          properties.getMaxLength(),
          idempotencyKey.length());
      return false;
    }

    // Validate UUID format using regex
    if (!UUID_PATTERN.matcher(idempotencyKey).matches()) {
      if (log.isDebugEnabled()) {
        log.debug("Idempotency-Key does not match UUID pattern: {}", idempotencyKey);
      }
      return false;
    }

    // Additional validation: try parsing as UUID
    try {
      UUID.fromString(idempotencyKey);
      if (log.isTraceEnabled()) {
        log.trace("Idempotency-Key validated as valid UUID: {}", idempotencyKey);
      }
      return true;
    } catch (IllegalArgumentException e) {
      log.warn("Idempotency-Key failed UUID parsing: {}", idempotencyKey);
      return false;
    }
  }

  /** Returns the set of HTTP methods that require idempotency checks. */
  public Set<String> getCheckableMethods() {
    return Set.copyOf(CHECKABLE_METHODS);
  }
}
