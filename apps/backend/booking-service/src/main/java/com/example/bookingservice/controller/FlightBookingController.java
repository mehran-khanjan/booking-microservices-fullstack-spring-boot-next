package com.example.bookingservice.controller;

import com.example.bookingservice.dto.controller.BookingResponse;
import com.example.bookingservice.dto.controller.CreateBookingRequest;
import com.example.bookingservice.dto.controller.PaymentRequest;
import com.example.bookingservice.servcie.BookingService;
import com.example.bookingservice.servcie.OrderService;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import com.example.commonlib.route.ApiRoutes;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for flight booking operations. Exposes endpoints to create bookings, process
 * payments, and handle fallbacks.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class FlightBookingController {

  private final BookingService bookingService;
  private final OrderService orderService;

  /**
   * Creates a new booking for the authenticated user. Holds seats for 15 minutes pending payment.
   *
   * @param request the booking details
   * @param authentication the current user's authentication
   * @return the created booking with a location header
   */
  @PostMapping(path = {ApiRoutes.Booking.BASE, ApiRoutes.Booking.BOOKING_TS})
  @PreAuthorize("hasAnyRole(@role.user(), @role.admin())")
  @CircuitBreaker(name = "bookingService", fallbackMethod = "createBookingFallback")
  public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
      @Valid @RequestBody CreateBookingRequest request, Authentication authentication) {

    String userId = extractUserId(authentication);

    log.info(
        "Create booking request from user: {}, flights: {}", userId, request.getFlights().size());

    BookingResponse response = bookingService.createBooking(request, userId);

    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", "/api/v1/bookings/" + response.getBookingReference())
        .body(
            ApiResponse.success(
                HttpStatus.CREATED,
                "Booking created successfully. Complete payment within hold period.",
                response));
  }

  /**
   * Fallback for createBooking when the circuit breaker trips.
   *
   * @param request the original request
   * @param authentication the user authentication
   * @param t the cause of the failure
   * @return a service unavailable response
   */
  private ResponseEntity<ApiResponse<BookingResponse>> createBookingFallback(
      CreateBookingRequest request, Authentication authentication, Throwable t) {

    log.error("Create booking fallback triggered: {}", t.getMessage());

    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiResponse.error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Booking service temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE.toString(),
                "SERVICE_UNAVAILABLE"));
  }

  /**
   * Processes payment for a booking and confirms it.
   *
   * @param request the payment details
   * @param authentication the current user's authentication
   * @return the payment confirmation with transaction details
   */
  @PostMapping(path = {ApiRoutes.Booking.PAYMENT, ApiRoutes.Booking.PAYMENT_TS})
  @PreAuthorize("hasAnyRole(@role.user(), @role.admin())")
  @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
  public ResponseEntity<ApiResponse<Map<String, String>>> processPayment(
      @Valid @RequestBody PaymentRequest request, Authentication authentication) {

    String userId = extractUserId(authentication);
    log.info(
        "Process payment request: booking={}, method={}, user={}",
        request.getBookingReference(),
        request.getPaymentMethod(),
        userId);

    String transactionId =
        orderService.handlePayment(
            request.getBookingReference(),
            request.getPaymentMethod(),
            request.getPaymentToken(),
            request.getIdempotencyKey(),
            userId,
            request.getAmount(),
            request.getCurrency());

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK,
            "Payment processed successfully. Booking confirmed.",
            Map.of(
                "bookingReference",
                request.getBookingReference(),
                "transactionId",
                transactionId,
                "amountPaid",
                request.getAmount().toPlainString())));
  }

  /**
   * Fallback for processPayment when the circuit breaker trips.
   *
   * @param request the original request
   * @param authentication the user authentication
   * @param t the cause of the failure
   * @return a service unavailable response
   */
  private ResponseEntity<ApiResponse<String>> processPaymentFallback(
      PaymentRequest request, Authentication authentication, Throwable t) {

    log.error("Process payment fallback triggered: {}", t.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiResponse.error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Payment service temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE.toString(),
                "SERVICE_UNAVAILABLE"));
  }

  // ========== Helper Methods ==========

  /**
   * Extracts the user ID from the authentication principal.
   *
   * @param authentication the Spring Security authentication
   * @return the user ID (JWT subject or authentication name)
   */
  private String extractUserId(Authentication authentication) {
    if (authentication.getPrincipal() instanceof Jwt jwt) {
      return jwt.getSubject();
    }
    return authentication.getName();
  }
}
