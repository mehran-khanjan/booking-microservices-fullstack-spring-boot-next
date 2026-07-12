package com.example.flightservice.exception;

import lombok.Getter;

/**
 * Custom runtime exception for business logic failures.
 *
 * <p>This exception is used to signal domain‑specific errors (e.g., insufficient seats, invalid
 * booking state, payment failures). Each instance carries a unique {@code errorCode} from the
 * {@link com.example.flightservice.constant.ErrorCodes} registry, allowing clients to
 * programmatically handle different error scenarios.
 *
 * <p>Throwing this exception typically results in a structured error response with the appropriate
 * HTTP status (e.g., 400 Bad Request, 409 Conflict, or 422 Unprocessable Entity).
 *
 * @see com.example.flightservice.constant.ErrorCodes
 * @see org.springframework.web.bind.annotation.ExceptionHandler
 */
@Getter
public class BusinessException extends RuntimeException {

  /** Unique error code identifying the specific failure. */
  private final String errorCode;

  /**
   * Constructs a new business exception with the given message and error code.
   *
   * @param message human‑readable description of the error
   * @param errorCode unique error code (e.g., {@code FLIGHT_001})
   */
  public BusinessException(String message, String errorCode) {
    super(message);
    this.errorCode = errorCode;
  }

  /**
   * Constructs a new business exception with the given message, error code, and cause.
   *
   * @param message human‑readable description of the error
   * @param errorCode unique error code
   * @param cause the underlying cause (e.g., a database constraint violation)
   */
  public BusinessException(String message, String errorCode, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }
}
