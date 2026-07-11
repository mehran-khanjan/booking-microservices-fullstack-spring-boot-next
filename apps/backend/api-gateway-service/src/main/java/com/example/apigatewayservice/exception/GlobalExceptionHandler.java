package com.example.apigatewayservice.exception;

import com.example.commonlib.responseenvelope.context.RequestContext;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the API Gateway service.
 *
 * <p>This class intercepts exceptions thrown by any {@code @RestController} or {@code @Controller}
 * within the application and provides a standardized error response format using {@link
 * ApiResponse}. It also ensures consistent logging of unexpected errors, including the distributed
 * trace ID for correlation.
 *
 * <p>The handler is registered as a Spring AOP advice via {@code @RestControllerAdvice}, allowing
 * it to catch exceptions from all controller layers without intrusive try-catch blocks in each
 * endpoint.
 *
 * <p>Currently, it catches {@link Exception} as a fallback for any unhandled exception, returning
 * an HTTP 500 Internal Server Error with a generic message suitable for production environments (no
 * internal stack traces exposed to clients). The error is logged at {@code ERROR} level including
 * the trace ID obtained from {@link RequestContext#getTraceId()} for easy debugging and monitoring.
 *
 * @author Your Name / Team
 * @version 1.0
 * @see ApiResponse
 * @see RequestContext
 * @since 2025-01-01
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /**
   * Handles any uncaught exception thrown by controller methods.
   *
   * <p>This method acts as a catch-all for any {@code Exception} not already handled by more
   * specific {@code @ExceptionHandler} methods in this advice or in controllers. It constructs a
   * generic internal error response, logs the exception with its stack trace and the current trace
   * ID, and returns an HTTP 500 response with a client-safe error message.
   *
   * <p><strong>Important:</strong> The trace ID is retrieved from {@link
   * RequestContext#getTraceId()}, which is expected to be populated earlier in the request
   * processing pipeline (e.g., via a filter or interceptor) with the value of the {@code
   * X-B3-TraceId} or similar correlation header. This ensures that the log entry can be correlated
   * with the distributed tracing system.
   *
   * <p>The returned {@link ApiResponse} follows the common envelope format used across the API,
   * with {@code status} set to {@code 500} and a user-friendly message. The {@code data} field is
   * {@code null} because no payload can be returned in case of an error.
   *
   * @param ex the exception that was thrown (never {@code null})
   * @param request the current HTTP request, providing additional context such as URI, headers,
   *     etc. (never {@code null})
   * @return a {@link ResponseEntity} containing an {@link ApiResponse} with an HTTP 500 status and
   *     a generic error message
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGlobalException(
      Exception ex, HttpServletRequest request) {

    ApiResponse<Void> response =
        ApiResponse.internalError(
            "An unexpected error occurred", "Internal server error - please contact support");

    log.error("Unexpected error [traceId: {}]: ", RequestContext.getTraceId(), ex);

    return ResponseEntity.status(response.getStatus()).body(response);
  }
}
