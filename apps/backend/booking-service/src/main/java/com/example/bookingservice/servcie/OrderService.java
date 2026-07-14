package com.example.bookingservice.servcie;

import com.example.bookingservice.constant.ErrorCodes;
import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.exception.BusinessException;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.servcie.payment.PaymentChargeRequest;
import com.example.bookingservice.servcie.payment.PaymentGatewayFactory;
import com.example.bookingservice.servcie.payment.PaymentGatewayResult;
import com.example.bookingservice.servcie.payment.PaymentGatewayService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that handles the payment and confirmation lifecycle of a booking. It validates the
 * booking state, charges the customer via the appropriate payment gateway, and updates the booking
 * status upon success.
 */
@Service
@AllArgsConstructor
@Slf4j
public class OrderService {

  private final BookingRepository bookingRepository;
  private final PaymentGatewayFactory paymentGatewayFactory;

  /**
   * Processes a payment for a booking.
   *
   * <p>Validates the booking state, ensures the requested amount matches the booking total,
   * delegates the charge to the appropriate payment gateway, and marks the booking as CONFIRMED on
   * success. On failure, the booking stays PENDING_PAYMENT for retry.
   *
   * @param bookingReference unique booking reference
   * @param paymentMethod method (e.g., "CREDIT_CARD", "PAYPAL")
   * @param paymentToken gateway-specific token from the frontend
   * @param idempotencyKey client idempotency key; a fallback is derived if blank
   * @param userId authenticated user ID
   * @param requestedAmount amount the client expects to pay (must match booking total)
   * @param requestedCurrency currency the client expects to use (must match booking currency)
   * @return the gateway transaction/capture ID
   * @throws BusinessException if validation fails, the booking is expired/cancelled, or the payment
   *     is declined
   */
  @Transactional
  public String handlePayment(
      String bookingReference,
      String paymentMethod,
      String paymentToken,
      String idempotencyKey,
      String userId,
      BigDecimal requestedAmount,
      String requestedCurrency) {

    Booking booking = loadBookingWithLock(bookingReference, userId);

    // Idempotency check: if already confirmed, return existing transaction ID
    if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
      log.info(
          "Booking {} already confirmed with transaction {}",
          bookingReference,
          booking.getPaymentTransactionId());
      return booking.getPaymentTransactionId();
    }

    validatePaymentAmount(booking, requestedAmount, requestedCurrency);
    validateBookingForPayment(booking);

    PaymentGatewayService gateway = paymentGatewayFactory.resolve(paymentMethod);

    String effectiveIdempotencyKey =
        (idempotencyKey != null && !idempotencyKey.isBlank())
            ? idempotencyKey
            : bookingReference + ":" + paymentMethod.toUpperCase();

    PaymentChargeRequest chargeRequest =
        PaymentChargeRequest.builder()
            .bookingReference(bookingReference)
            .amount(booking.getTotalAmount())
            .currency(booking.getCurrency())
            .paymentToken(paymentToken)
            .idempotencyKey(effectiveIdempotencyKey)
            .build();

    log.info(
        "Charging booking {} via {} (idempotencyKey={})",
        bookingReference,
        gateway.getProviderName(),
        effectiveIdempotencyKey);

    PaymentGatewayResult result = gateway.charge(chargeRequest);

    if (!result.isSuccess()) {
      log.warn(
          "Payment declined for booking {} via {}: {}",
          bookingReference,
          gateway.getProviderName(),
          result.getFailureReason());
      throw new BusinessException(
          "Payment was declined: " + result.getFailureReason(), ErrorCodes.PAYMENT_DECLINED);
    }

    booking.setStatus(Booking.BookingStatus.CONFIRMED);
    booking.setPaymentTransactionId(result.getTransactionId());
    booking.setConfirmedAt(LocalDateTime.now());
    bookingRepository.save(booking);

    log.info(
        "Booking {} confirmed via {} with transaction {}",
        bookingReference,
        gateway.getProviderName(),
        result.getTransactionId());

    return result.getTransactionId();
  }

  // ------------------------------------------------------------------------
  // Private helper methods
  // ------------------------------------------------------------------------

  /** Loads the booking with a pessimistic write lock and verifies user ownership. */
  private Booking loadBookingWithLock(String bookingReference, String userId) {
    Booking booking =
        bookingRepository
            .findByBookingReferenceWithLock(bookingReference)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Booking not found: " + bookingReference, ErrorCodes.BOOKING_NOT_FOUND));

    if (!booking.getUserId().equals(userId)) {
      throw new BusinessException("Unauthorized access", ErrorCodes.VALIDATION_ERROR);
    }
    return booking;
  }

  /**
   * Validates that the booking is in a payable state (not cancelled, not expired). If the hold has
   * expired, marks the booking as EXPIRED and throws.
   */
  private void validateBookingForPayment(Booking booking) {
    if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
      throw new BusinessException("Booking is cancelled", ErrorCodes.INVALID_BOOKING_STATE);
    }
    if (booking.getStatus() == Booking.BookingStatus.EXPIRED) {
      throw new BusinessException("Booking has expired", ErrorCodes.BOOKING_EXPIRED);
    }
    if (booking.getHoldExpiresAt() != null
        && booking.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
      booking.setStatus(Booking.BookingStatus.EXPIRED);
      bookingRepository.save(booking);
      throw new BusinessException("Booking hold has expired", ErrorCodes.BOOKING_EXPIRED);
    }
  }

  /** Verifies that the client‑supplied amount and currency exactly match the booking's total. */
  private void validatePaymentAmount(
      Booking booking, BigDecimal requestedAmount, String requestedCurrency) {
    if (requestedAmount == null) {
      throw new BusinessException("Payment amount is required", ErrorCodes.VALIDATION_ERROR);
    }
    if (requestedCurrency == null || requestedCurrency.isBlank()) {
      throw new BusinessException("Payment currency is required", ErrorCodes.VALIDATION_ERROR);
    }

    BigDecimal expected = booking.getTotalAmount();

    if (expected.compareTo(requestedAmount) != 0) {
      throw new BusinessException(
          String.format(
              "Payment amount %s %s does not match booking total %s %s",
              requestedAmount, requestedCurrency, expected, booking.getCurrency()),
          ErrorCodes.PAYMENT_AMOUNT_MISMATCH);
    }

    if (!booking.getCurrency().equalsIgnoreCase(requestedCurrency)) {
      throw new BusinessException(
          String.format(
              "Payment currency '%s' does not match booking currency '%s'",
              requestedCurrency, booking.getCurrency()),
          ErrorCodes.PAYMENT_AMOUNT_MISMATCH);
    }
  }
}
