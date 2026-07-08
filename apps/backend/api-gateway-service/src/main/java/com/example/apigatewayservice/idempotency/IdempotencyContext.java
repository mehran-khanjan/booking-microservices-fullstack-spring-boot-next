package com.example.apigatewayservice.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Immutable per‑request context that validates and provides the {@code Idempotency-Key} for non‑GET
 * requests.
 *
 * <ul>
 *   <li>Validates the header (required for mutating operations, strict UUID format)
 *   <li>Injects the key into MDC for structured logging
 *   <li>Wraps the request to expose the header downstream
 *   <li>Auto‑clears MDC via {@link AutoCloseable}
 * </ul>
 *
 * <p>Not a Spring bean – instantiated per‑request to avoid shared state.
 */
public final class IdempotencyContext implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyContext.class);

  public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  private final String idempotencyKey; // nullable - optional header
  private final IdempotencyRequestWrapper wrappedRequest;

  private IdempotencyContext(HttpServletRequest request, String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
    this.wrappedRequest = new IdempotencyRequestWrapper(request, idempotencyKey);

    if (log.isDebugEnabled()) {
      log.debug(
          "IdempotencyContext created for request: {} {}, idempotencyKey={}",
          request.getMethod(),
          request.getRequestURI(),
          idempotencyKey != null ? idempotencyKey : "not-provided");
    }
  }

  /**
   * Factory method: validates Idempotency-Key if present for non-GET requests.
   *
   * @param request the original HTTP request
   * @param response the HTTP response (used to set error status on failure)
   * @param validator the idempotency validator
   * @return a new context if validation passes, or {@code null} if validation fails
   */
  public static IdempotencyContext validateFor(
      HttpServletRequest request, HttpServletResponse response, IdempotencyValidator validator) {
    String method = request.getMethod();
    String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

    // Check if this method requires idempotency validation
    if (!validator.requiresIdempotencyCheck(method)) {
      if (log.isDebugEnabled()) {
        log.debug("Method {} does not require idempotency check - skipping", method);
      }
      return new IdempotencyContext(request, null);
    }

    // Key is REQUIRED for mutating operations
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      log.warn("Idempotency-Key REQUIRED but not provided for {} request", method);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return null; // ❌ Reject request
    }

    // Key provided - validate UUID format (strict mode)
    if (!validator.isValidUUID(idempotencyKey)) {
      log.warn("Invalid Idempotency-Key format provided: {} (must be valid UUID)", idempotencyKey);
      return null; // Validation failed - caller must handle error
    }

    log.info("Valid Idempotency-Key found: {}", idempotencyKey);

    IdempotencyContext ctx = new IdempotencyContext(request, idempotencyKey);
    ctx.applyToMdc();

    return ctx;
  }

  /** Returns the wrapped request with idempotency key accessible. Use this in your filter chain. */
  public HttpServletRequest getWrappedRequest() {
    if (log.isTraceEnabled()) {
      log.trace("Returning wrapped request with idempotency context");
    }
    return wrappedRequest;
  }

  public Optional<String> getIdempotencyKey() {
    return Optional.ofNullable(idempotencyKey);
  }

  private void applyToMdc() {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      MDC.put("idempotencyKey", idempotencyKey);
      if (log.isDebugEnabled()) {
        log.debug("MDC populated with idempotencyKey={}", idempotencyKey);
      }
    }
  }

  /**
   * Clears MDC idempotency key to prevent thread-pool pollution. Called automatically when used in
   * try-with-resources.
   */
  @Override
  public void close() {
    if (idempotencyKey != null) {
      if (log.isDebugEnabled()) {
        log.debug("Removing idempotencyKey from MDC: {}", idempotencyKey);
      }
      MDC.remove("idempotencyKey");
    }
  }

  /**
   * Inner request wrapper that provides access to idempotency key. Handles case-insensitive header
   * matching per HTTP spec.
   */
  private static final class IdempotencyRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> idempotencyHeaders;

    IdempotencyRequestWrapper(HttpServletRequest request, String idempotencyKey) {
      super(request);
      this.idempotencyHeaders = new HashMap<>(1);
      if (idempotencyKey != null && !idempotencyKey.isBlank()) {
        idempotencyHeaders.put(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
      }
      if (log.isTraceEnabled()) {
        log.trace("IdempotencyRequestWrapper created with headers: {}", idempotencyHeaders);
      }
    }

    @Override
    public String getHeader(String name) {
      String idempotencyValue = getIdempotencyHeader(name);
      if (idempotencyValue != null) {
        if (log.isTraceEnabled()) {
          log.trace("getHeader('{}') returning idempotency value: {}", name, idempotencyValue);
        }
        return idempotencyValue;
      }
      String original = super.getHeader(name);
      if (log.isTraceEnabled()) {
        log.trace("getHeader('{}') returning original value: {}", name, original);
      }
      return original;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      String idempotencyValue = getIdempotencyHeader(name);
      if (idempotencyValue != null) {
        List<String> values = new ArrayList<>();
        values.add(idempotencyValue);
        Enumeration<String> originalHeaders = super.getHeaders(name);
        while (originalHeaders.hasMoreElements()) {
          String original = originalHeaders.nextElement();
          if (!idempotencyValue.equals(original)) {
            values.add(original);
          }
        }
        if (log.isTraceEnabled()) {
          log.trace("getHeaders('{}') returning merged values: {}", name, values);
        }
        return Collections.enumeration(values);
      }
      Enumeration<String> original = super.getHeaders(name);
      if (log.isTraceEnabled()) {
        log.trace("getHeaders('{}') returning original enumeration", name);
      }
      return original;
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Set<String> names = new LinkedHashSet<>();
      names.addAll(idempotencyHeaders.keySet());
      Enumeration<String> originalNames = super.getHeaderNames();
      while (originalNames.hasMoreElements()) {
        names.add(originalNames.nextElement());
      }
      if (log.isTraceEnabled()) {
        log.trace("getHeaderNames() returning: {}", names);
      }
      return Collections.enumeration(names);
    }

    private String getIdempotencyHeader(String name) {
      if (name == null) return null;
      for (Map.Entry<String, String> entry : idempotencyHeaders.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue();
        }
      }
      return null;
    }
  }
}
