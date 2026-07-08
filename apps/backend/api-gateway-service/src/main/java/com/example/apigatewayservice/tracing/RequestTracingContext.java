package com.example.apigatewayservice.tracing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Immutable per‑request context that manages distributed tracing identifiers.
 *
 * <ul>
 *   <li>Resolves or generates {@code X-Correlation-ID}, {@code X-Request-ID}, and optionally {@code
 *       X-Transaction-ID}
 *   <li>Injects IDs into MDC for structured logging
 *   <li>Wraps the request to propagate headers downstream
 *   <li>Echoes the IDs back in response headers
 *   <li>Auto‑clears MDC via {@link AutoCloseable}
 * </ul>
 */
public final class RequestTracingContext implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(RequestTracingContext.class);

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  public static final String REQUEST_ID_HEADER = "X-Request-ID";
  public static final String TRANSACTION_ID_HEADER = "X-Transaction-ID";

  private final String correlationId;
  private final String requestId;
  private final String transactionId; // nullable
  private final TracingRequestWrapper wrappedRequest;

  private RequestTracingContext(
      HttpServletRequest request, String correlationId, String requestId, String transactionId) {
    this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
    this.requestId = Objects.requireNonNull(requestId, "requestId");
    this.transactionId = transactionId;
    this.wrappedRequest =
        new TracingRequestWrapper(request, correlationId, requestId, transactionId);

    if (log.isDebugEnabled()) {
      log.debug(
          "RequestTracingContext created for request: {} {}, correlationId={}, requestId={}, transactionId={}",
          request.getMethod(),
          request.getRequestURI(),
          correlationId,
          requestId,
          transactionId);
    }
  }

  /**
   * Factory method: resolves IDs from request (generating if absent), applies to MDC, echoes on
   * response, and returns wrapped request.
   *
   * @param request the original HTTP request
   * @param response the HTTP response (to set tracing headers)
   * @return a new tracing context
   */
  public static RequestTracingContext startFor(
      HttpServletRequest request, HttpServletResponse response) {
    // ----- 1. Resolve each ID with explicit logging -----
    String correlationId =
        resolveWithLogging(
            request.getHeader(CORRELATION_ID_HEADER), CORRELATION_ID_HEADER, "correlationId");
    String requestId =
        resolveWithLogging(request.getHeader(REQUEST_ID_HEADER), REQUEST_ID_HEADER, "requestId");
    // Transaction ID is optional – log its presence/absence separately
    String transactionId = request.getHeader(TRANSACTION_ID_HEADER);
    if (transactionId != null && !transactionId.isBlank()) {
      log.info("Transaction-ID found in request header: {}", transactionId);
    } else {
      log.info("Transaction-ID not provided (optional) – will not be set");
    }

    // ----- 2. Summary of final resolved values -----
    log.info(
        "Tracing IDs resolved – correlationId: {}, requestId: {}, transactionId: {}",
        correlationId,
        requestId,
        transactionId != null && !transactionId.isBlank() ? transactionId : "N/A");

    // ----- 3. Create context, apply MDC, echo headers -----
    RequestTracingContext ctx =
        new RequestTracingContext(request, correlationId, requestId, transactionId);

    ctx.applyToMdc();
    ctx.echoOnResponse(response);

    return ctx;
  }

  /** Helper that logs whether the ID came from the header or was generated. */
  private static String resolveWithLogging(String headerValue, String headerName, String idType) {
    if (headerValue != null && !headerValue.isBlank()) {
      log.info("{} found in request header ({}): {}", idType, headerName, headerValue);
      return headerValue;
    } else {
      String generated = UUID.randomUUID().toString();
      log.info("{} not provided – generating new: {}", idType, generated);
      return generated;
    }
  }

  /** Returns the wrapped request with tracing headers injected. Use this in your filter chain. */
  public HttpServletRequest getWrappedRequest() {
    if (log.isTraceEnabled()) {
      log.trace("Returning wrapped request with tracing headers");
    }
    return wrappedRequest;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public String getRequestId() {
    return requestId;
  }

  public Optional<String> getTransactionId() {
    return Optional.ofNullable(transactionId);
  }

  private void applyToMdc() {
    MDC.put("correlationId", correlationId);
    MDC.put("requestId", requestId);
    if (transactionId != null && !transactionId.isBlank()) {
      MDC.put("transactionId", transactionId);
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "MDC populated with correlationId={}, requestId={}, transactionId={}",
          correlationId,
          requestId,
          transactionId);
    }
  }

  private void echoOnResponse(HttpServletResponse response) {
    response.setHeader(CORRELATION_ID_HEADER, correlationId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    if (log.isDebugEnabled()) {
      log.debug(
          "Echoed tracing headers on response: {}={}, {}={}",
          CORRELATION_ID_HEADER,
          correlationId,
          REQUEST_ID_HEADER,
          requestId);
    }
  }

  /**
   * Clears MDC to prevent thread-pool pollution. Called automatically when used in
   * try-with-resources.
   */
  @Override
  public void close() {
    if (log.isInfoEnabled()) {
      log.info(
          "Clearing MDC for request: correlationId={}, requestId={}", correlationId, requestId);
    }
    MDC.clear();
  }

  // The original resolveOrGenerate is no longer needed – replaced by resolveWithLogging
  // (kept only for backward compatibility if used elsewhere, but can be removed)
  private static String resolveOrGenerate(String headerValue) {
    return (headerValue != null && !headerValue.isBlank())
        ? headerValue
        : UUID.randomUUID().toString();
  }

  /**
   * Inner request wrapper that injects tracing headers for downstream consumers. Handles
   * case-insensitive header matching per HTTP spec.
   */
  private static final class TracingRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> tracingHeaders;

    TracingRequestWrapper(
        HttpServletRequest request, String correlationId, String requestId, String transactionId) {
      super(request);
      this.tracingHeaders = new HashMap<>(3);
      tracingHeaders.put(CORRELATION_ID_HEADER, correlationId);
      tracingHeaders.put(REQUEST_ID_HEADER, requestId);
      if (transactionId != null && !transactionId.isBlank()) {
        tracingHeaders.put(TRANSACTION_ID_HEADER, transactionId);
      }
      if (log.isTraceEnabled()) {
        log.trace("TracingRequestWrapper created with headers: {}", tracingHeaders);
      }
    }

    @Override
    public String getHeader(String name) {
      String tracingValue = getTracingHeader(name);
      if (tracingValue != null) {
        if (log.isTraceEnabled()) {
          log.trace("getHeader('{}') returning tracing value: {}", name, tracingValue);
        }
        return tracingValue;
      }
      String original = super.getHeader(name);
      if (log.isTraceEnabled()) {
        log.trace("getHeader('{}') returning original value: {}", name, original);
      }
      return original;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      String tracingValue = getTracingHeader(name);
      if (tracingValue != null) {
        List<String> values = new ArrayList<>();
        values.add(tracingValue);
        Enumeration<String> originalHeaders = super.getHeaders(name);
        while (originalHeaders.hasMoreElements()) {
          String original = originalHeaders.nextElement();
          if (!tracingValue.equals(original)) {
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
      names.addAll(tracingHeaders.keySet());
      Enumeration<String> originalNames = super.getHeaderNames();
      while (originalNames.hasMoreElements()) {
        names.add(originalNames.nextElement());
      }
      if (log.isTraceEnabled()) {
        log.trace("getHeaderNames() returning: {}", names);
      }
      return Collections.enumeration(names);
    }

    private String getTracingHeader(String name) {
      if (name == null) return null;
      for (Map.Entry<String, String> entry : tracingHeaders.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue();
        }
      }
      return null;
    }
  }
}
