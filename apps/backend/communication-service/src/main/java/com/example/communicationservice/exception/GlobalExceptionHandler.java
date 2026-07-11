package com.example.communicationservice.exception;

import com.example.commonlib.responseenvelope.context.RequestContext;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the Communication Service.
 *
 * <p>Catches all unhandled exceptions and returns a consistent {@link ApiResponse} with an internal
 * error status. The trace ID from {@link RequestContext} is included in the log for correlation.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  /**
   * Handles any {@link Exception} thrown by controllers.
   *
   * <p>Logs the error with the current trace ID and returns a generic internal server error
   * response.
   *
   * @param ex the exception that occurred
   * @param request the HTTP request (used for context)
   * @return a {@link ResponseEntity} containing an {@link ApiResponse} with status {@code 500}
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
