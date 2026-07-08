package com.example.apigatewayservice.filter;

import com.example.apigatewayservice.forwarding.ForwardedHeaderRequestWrapper;
import com.example.apigatewayservice.idempotency.IdempotencyContext;
import com.example.apigatewayservice.idempotency.IdempotencyProperties;
import com.example.apigatewayservice.idempotency.IdempotencyValidator;
import com.example.apigatewayservice.locale.LocaleContext;
import com.example.apigatewayservice.locale.LocaleProperties;
import com.example.apigatewayservice.locale.LocaleValidator;
import com.example.apigatewayservice.tracing.RequestTracingContext;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gateway edge filter that orchestrates the layered validation pipeline:
 *
 * <ol>
 *   <li>Distributed tracing (correlation, request, transaction IDs)
 *   <li>Forwarded‑header synthesis (X‑Forwarded‑For, etc.)
 *   <li>Locale validation (Accept‑Language)
 *   <li>Idempotency validation (Idempotency‑Key for mutating operations)
 * </ol>
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} to ensure tracing context is available for all
 * downstream filters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayEdgeFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(GatewayEdgeFilter.class);

  // Paths that bypass validation checks but still get tracing
  private static final Set<String> DIAGNOSTIC_PATH_PREFIXES =
      Set.of("/actuator", "/swagger", "/v3/api-docs");

  private final IdempotencyValidator idempotencyValidator;
  private final IdempotencyProperties idempotencyProperties;
  private final LocaleValidator localeValidator;
  private final LocaleProperties localeProperties;
  private final ObjectMapper objectMapper;

  public GatewayEdgeFilter(
      IdempotencyValidator idempotencyValidator,
      IdempotencyProperties idempotencyProperties,
      LocaleValidator localeValidator,
      LocaleProperties localeProperties,
      ObjectMapper objectMapper) {
    this.idempotencyValidator = idempotencyValidator;
    this.idempotencyProperties = idempotencyProperties;
    this.localeValidator = localeValidator;
    this.localeProperties = localeProperties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();
    String method = request.getMethod();
    long startNanos = System.nanoTime();

    // Layer 0: Tracing context (use try-with-resources for automatic MDC cleanup)
    try (RequestTracingContext tracing = RequestTracingContext.startFor(request, response)) {

      HttpServletRequest tracingRequest = tracing.getWrappedRequest();

      // Layer 1: Forwarded headers (X-Forwarded-For, etc.)
      HttpServletRequest forwardedRequest = ForwardedHeaderRequestWrapper.wrap(tracingRequest);

      boolean isDiagnostic = isDiagnosticPath(path);

      // Layer 2: Locale validation (skip for diagnostic paths)
      if (localeProperties.isEnabled() && !isDiagnostic) {

        LocaleContext locale =
            LocaleContext.validateFor(forwardedRequest, response, localeValidator);

        // Validation failed - return error response
        if (locale == null) {
          writeLocaleErrorResponse(response, tracing.getCorrelationId());
          return;
        }

        // Locale validation passed - continue with wrapped request
        try (locale) {
          HttpServletRequest localeRequest = locale.getWrappedRequest();

          // Layer 3: Idempotency validation
          processWithIdempotency(
              localeRequest,
              response,
              filterChain,
              path,
              method,
              startNanos,
              isDiagnostic,
              tracing);
        } // Auto-closes locale context, clearing MDC

      } else {
        // Locale validation disabled or diagnostic path - skip
        processWithIdempotency(
            forwardedRequest,
            response,
            filterChain,
            path,
            method,
            startNanos,
            isDiagnostic,
            tracing);
      }
    } // Auto-closes tracing context, clearing MDC
  }

  /** Processes request with idempotency validation layer. */
  private void processWithIdempotency(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain,
      String path,
      String method,
      long startNanos,
      boolean isDiagnostic,
      RequestTracingContext tracing)
      throws ServletException, IOException {

    if (idempotencyProperties.isEnabled() && !isDiagnostic) {

      IdempotencyContext idempotency =
          IdempotencyContext.validateFor(request, response, idempotencyValidator);

      // Validation failed - return error response
      if (idempotency == null) {
        writeIdempotencyErrorResponse(response, tracing.getCorrelationId());
        return;
      }

      // Validation passed - continue with wrapped request
      try (idempotency) {
        HttpServletRequest idempotentRequest = idempotency.getWrappedRequest();

        try {
          filterChain.doFilter(idempotentRequest, response);
          logRequestCompletion(path, method, response.getStatus(), startNanos);

        } catch (Exception ex) {
          log.error(
              "Unhandled exception in filter chain: path={} method={} correlationId={} error={}",
              path,
              method,
              tracing.getCorrelationId(),
              ex.getMessage(),
              ex);
          throw ex;
        }
      } // Auto-closes idempotency context, clearing MDC

    } else {
      // Idempotency disabled or diagnostic path - skip validation
      try {
        filterChain.doFilter(request, response);

        if (!isDiagnostic) {
          logRequestCompletion(path, method, response.getStatus(), startNanos);
        }

      } catch (Exception ex) {
        log.error(
            "Unhandled exception in filter chain: path={} method={} correlationId={} error={}",
            path,
            method,
            tracing.getCorrelationId(),
            ex.getMessage(),
            ex);
        throw ex;
      }
    }
  }

  private boolean isDiagnosticPath(String path) {
    return DIAGNOSTIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
  }

  private void logRequestCompletion(String path, String method, int status, long startNanos) {
    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

    if (status >= 500) {
      log.error(
          "Request failed: method={} path={} status={} durationMs={}",
          method,
          path,
          status,
          durationMs);
    } else if (status >= 400) {
      log.warn(
          "Client error: method={} path={} status={} durationMs={}",
          method,
          path,
          status,
          durationMs);
    } else {
      log.info(
          "Request completed: method={} path={} status={} durationMs={}",
          method,
          path,
          status,
          durationMs);
    }
  }

  /**
   * Writes a JSON error response when Accept-Language validation fails. Uses the ApiResponse format
   * from common-lib-imp.
   */
  private void writeLocaleErrorResponse(HttpServletResponse response, String traceId)
      throws IOException {

    String errorMessage =
        localeProperties.isRequired()
            ? "Accept-Language header is required but not provided"
            : "Accept-Language header has invalid format";

    String errorDetail = buildLocaleErrorDetail();

    ApiResponse<Void> errorResponse = ApiResponse.badRequest(errorMessage, errorDetail);

    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    String jsonResponse = objectMapper.writeValueAsString(errorResponse);
    response.getWriter().write(jsonResponse);
    response.getWriter().flush();

    log.warn(
        "Accept-Language validation failed: returning 400 Bad Request with traceId={}", traceId);
  }

  /** Builds detailed error message for locale validation failure. */
  private String buildLocaleErrorDetail() {
    StringBuilder detail = new StringBuilder();

    detail.append("Accept-Language header must be a valid language tag ");
    detail.append("(e.g., 'en', 'en-US', 'fr-FR'). ");

    if (!localeProperties.getSupportedLanguages().isEmpty()) {
      detail
          .append("Supported languages: ")
          .append(String.join(", ", localeProperties.getSupportedLanguages()))
          .append(". ");
    }

    if (!localeProperties.getSupportedLocales().isEmpty()) {
      detail
          .append("Supported locales: ")
          .append(String.join(", ", localeProperties.getSupportedLocales()))
          .append(".");
    }

    return detail.toString().trim();
  }

  /**
   * Writes a JSON error response when Idempotency-Key validation fails. Uses the ApiResponse format
   * from common-lib-imp.
   */
  private void writeIdempotencyErrorResponse(HttpServletResponse response, String traceId)
      throws IOException {

    ApiResponse<Void> errorResponse =
        ApiResponse.badRequest(
            "Invalid Idempotency-Key format",
            "Idempotency-Key header must be a valid UUID (e.g., 550e8400-e29b-41d4-a716-446655440000)");

    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    String jsonResponse = objectMapper.writeValueAsString(errorResponse);
    response.getWriter().write(jsonResponse);
    response.getWriter().flush();

    log.warn("Idempotency validation failed: returning 400 Bad Request with traceId={}", traceId);
  }
}
