package com.example.flightservice.exception;

import com.example.commonlib.responseenvelope.context.RequestContext;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import com.example.commonlib.responseenvelope.response.ApiResponse.ValidationError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the flight service.
 *
 * <p>This class intercepts exceptions thrown by controllers and provides consistent, structured
 * error responses using the {@link ApiResponse} envelope. It handles:
 *
 * <ul>
 *   <li>Validation failures (request body, query parameters, path variables, etc.)
 *   <li>Malformed JSON payloads
 *   <li>All other unexpected exceptions as a fallback
 * </ul>
 *
 * <p>All error responses include a trace ID obtained from {@link RequestContext#getTraceId()} for
 * distributed tracing and correlation with logs. Exceptions are logged at appropriate levels (warn
 * for validation errors, error for unexpected exceptions) along with the trace ID.
 *
 * <p>The handler is registered via {@link RestControllerAdvice} and applies to all controllers in
 * the application context.
 *
 * @author Your Team
 * @see ApiResponse
 * @see RequestContext
 * @since 1.0
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  // ========================================
  // Validation Exceptions
  // ========================================

  /**
   * Handles validation errors when {@code @Valid} is used on a request body (e.g.,
   * {@code @RequestBody @Valid SomeDto}).
   *
   * <p>This exception is thrown by Spring when a {@link MethodArgumentNotValidException} occurs
   * during validation of the request body. The method extracts all field errors, converts them to
   * {@link ValidationError} objects, and returns an HTTP 400 Bad Request response with a structured
   * error list.
   *
   * <p>The error details are logged at WARN level, including the trace ID.
   *
   * @param ex the exception containing validation errors
   * @param request the current HTTP request (provides additional context)
   * @return a {@link ResponseEntity} with HTTP 400 status and an {@code ApiResponse} containing a
   *     list of validation errors
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {

    List<ValidationError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(this::toValidationError)
            .collect(Collectors.toList());

    ApiResponse<Void> response =
        ApiResponse.validationError("Validation failed for one or more fields", errors);

    log.warn(
        "Validation error [traceId: {}]: {}", RequestContext.getTraceId(), response.getMessage());

    return ResponseEntity.status(response.getStatus()).body(response);
  }

  /**
   * Handles validation errors for query parameters, path variables, or form data.
   *
   * <p>This exception occurs when {@code @Valid} is used on method parameters that are not request
   * bodies (e.g., {@code @RequestParam}, {@code @PathVariable}, or form submission). The error
   * processing is identical to the request-body validation handler, extracting field errors and
   * returning an HTTP 400 response.
   *
   * @param ex the exception containing binding errors
   * @param request the current HTTP request
   * @return a {@link ResponseEntity} with HTTP 400 status and an {@code ApiResponse} containing
   *     validation error details
   */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiResponse<Void>> handleBindException(
      BindException ex, HttpServletRequest request) {

    List<ValidationError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(this::toValidationError)
            .collect(Collectors.toList());

    ApiResponse<Void> response =
        ApiResponse.validationError("Validation failed for one or more fields", errors);

    log.warn(
        "Bind validation error [traceId: {}]: {}",
        RequestContext.getTraceId(),
        response.getMessage());

    return ResponseEntity.status(response.getStatus()).body(response);
  }

  /**
   * Handles method-level validation constraints (e.g., {@code @Min}, {@code @NotBlank} on
   * controller method parameters when {@code @Validated} is used at class level).
   *
   * <p>This exception is thrown by Spring when a constraint violation occurs on a method parameter
   * (like a path variable or request parameter) that is validated via the {@code @Validated}
   * annotation. Each constraint violation is transformed into a {@link ValidationError} and
   * returned in the HTTP 400 response.
   *
   * @param ex the exception containing constraint violations
   * @param request the current HTTP request
   * @return a {@link ResponseEntity} with HTTP 400 status and an {@code ApiResponse} containing the
   *     violation details
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {

    List<ValidationError> errors =
        ex.getConstraintViolations().stream()
            .map(this::toValidationError)
            .collect(Collectors.toList());

    ApiResponse<Void> response =
        ApiResponse.validationError("Validation failed for one or more fields", errors);

    log.warn(
        "Constraint violation [traceId: {}]: {}",
        RequestContext.getTraceId(),
        response.getMessage());

    return ResponseEntity.status(response.getStatus()).body(response);
  }

  /**
   * Handles malformed JSON or otherwise unreadable request payloads.
   *
   * <p>This exception occurs when the request body cannot be parsed into the expected Java object
   * (e.g., missing required fields, invalid data types, or syntax errors). A generic HTTP 400 Bad
   * Request response is returned with a user-friendly message. The error is logged at WARN level
   * with the trace ID.
   *
   * @param ex the exception containing the parse error
   * @param request the current HTTP request
   * @return a {@link ResponseEntity} with HTTP 400 status and an {@code ApiResponse} indicating a
   *     malformed payload
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {

    ApiResponse<Void> response =
        ApiResponse.badRequest(
            "Malformed request payload",
            "The request body is invalid or could not be parsed. Please check the JSON syntax.");

    log.warn("Malformed JSON [traceId: {}]: {}", RequestContext.getTraceId(), ex.getMessage());

    return ResponseEntity.status(response.getStatus()).body(response);
  }

  // ========================================
  // Fallback for Any Other Exception
  // ========================================

  /**
   * Fallback handler for any unhandled exception not caught by more specific handlers.
   *
   * <p>This method acts as a catch-all for any {@link Exception} that propagates out of the
   * controllers. It returns a generic HTTP 500 Internal Server Error response with a message
   * suitable for production (no internal stack traces exposed to the client). The full exception
   * with stack trace is logged at ERROR level, including the trace ID for debugging.
   *
   * @param ex the unhandled exception
   * @param request the current HTTP request
   * @return a {@link ResponseEntity} with HTTP 500 status and a generic error message
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

  // ========================================
  // Private Helper Methods
  // ========================================

  /**
   * Converts a Spring {@link FieldError} into a {@link ValidationError} DTO.
   *
   * <p>This method extracts the field name, the default error message, and the rejected value from
   * the field error, and builds a {@code ValidationError} object for inclusion in the API response.
   *
   * @param fieldError the Spring field error (never {@code null})
   * @return a {@link ValidationError} with the extracted details
   */
  private ValidationError toValidationError(FieldError fieldError) {
    return ValidationError.builder()
        .field(fieldError.getField())
        .message(fieldError.getDefaultMessage())
        .rejectedValue(fieldError.getRejectedValue())
        .build();
  }

  /**
   * Converts a Jakarta {@link ConstraintViolation} into a {@link ValidationError} DTO.
   *
   * <p>This method extracts the property path (field name), the violation message, and the invalid
   * value from the constraint violation, and builds a {@code ValidationError} object for inclusion
   * in the API response.
   *
   * @param violation the constraint violation (never {@code null})
   * @return a {@link ValidationError} with the extracted details
   */
  private ValidationError toValidationError(ConstraintViolation<?> violation) {
    return ValidationError.builder()
        .field(violation.getPropertyPath().toString())
        .message(violation.getMessage())
        .rejectedValue(violation.getInvalidValue())
        .build();
  }
}
