package com.example.flightservice.constant;

/**
 * Central registry of error codes used throughout the Flight Service.
 *
 * <p>Each code is a unique string that can be used in error responses to help clients identify the
 * nature of a failure without parsing human-readable messages.
 *
 * <p>Codes are grouped by domain:
 *
 * <ul>
 *   <li><b>FLIGHT_xxx</b> – flight availability, search, or seat issues
 *   <li><b>BOOKING_xxx</b> – booking lifecycle and validation errors
 *   <li><b>PAYMENT_xxx</b> – payment and refund problems
 *   <li><b>CONCURRENCY_xxx</b> – optimistic locking or distributed lock failures
 *   <li><b>VALIDATION_xxx</b> – general input validation errors
 *   <li><b>SEARCH_TIMEOUT_xxx</b> – time‑out during flight search operations
 * </ul>
 *
 * This class is not meant to be instantiated.
 */
public final class ErrorCodes {

  // Flight Errors
  public static final String FLIGHT_NOT_FOUND = "FLIGHT_001";
  public static final String FLIGHT_NOT_AVAILABLE = "FLIGHT_002";
  public static final String INSUFFICIENT_SEATS = "FLIGHT_003";
  public static final String INVALID_SEARCH_PARAMS = "FLIGHT_004";

  // Booking Errors
  public static final String BOOKING_NOT_FOUND = "BOOKING_001";
  public static final String BOOKING_EXPIRED = "BOOKING_002";
  public static final String BOOKING_ALREADY_CONFIRMED = "BOOKING_003";
  public static final String BOOKING_CANNOT_BE_MODIFIED = "BOOKING_004";
  public static final String INVALID_BOOKING_STATE = "BOOKING_005";

  // Payment Errors
  public static final String PAYMENT_FAILED = "PAYMENT_001";
  public static final String PAYMENT_DECLINED = "PAYMENT_002";
  public static final String REFUND_FAILED = "PAYMENT_003";

  // Concurrency Errors
  public static final String OPTIMISTIC_LOCK_FAILURE = "CONCURRENCY_001";
  public static final String LOCK_ACQUISITION_FAILED = "CONCURRENCY_002";

  // Validation Errors
  public static final String VALIDATION_ERROR = "VALIDATION_001";

  public static final String SEARCH_TIMEOUT = "SEARCH_TIMEOUT_001";

  private ErrorCodes() {
    // Prevent instantiation
  }
}
