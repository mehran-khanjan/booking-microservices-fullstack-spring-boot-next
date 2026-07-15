package com.example.bookingservice.unit;

import com.example.bookingservice.constant.ErrorCodes;
import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.exception.BusinessException;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.servcie.OrderService;
import com.example.bookingservice.servcie.payment.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private PaymentGatewayFactory paymentGatewayFactory;
  @Mock private PaymentGatewayService paymentGateway;

  private OrderService orderService;

  private Booking booking;

  @BeforeEach
  void setUp() {
    orderService = new OrderService(bookingRepository, paymentGatewayFactory);

    booking = new Booking();
    booking.setId(1L);
    booking.setBookingReference("ABC123");
    booking.setUserId("user-123");
    booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
    booking.setTotalAmount(BigDecimal.valueOf(230));
    booking.setCurrency("USD");
    booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(15));
  }

  @Nested
  class HandlePayment {

    @Test
    void shouldProcessPaymentSuccessfully() {
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));
      when(paymentGatewayFactory.resolve("CREDIT_CARD")).thenReturn(paymentGateway);
      when(paymentGateway.getProviderName()).thenReturn("STRIPE");
      when(paymentGateway.charge(any(PaymentChargeRequest.class)))
          .thenReturn(PaymentGatewayResult.success("txn-123", "succeeded"));

      String txnId = orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(230), "USD");

      assertThat(txnId).isEqualTo("txn-123");
      assertThat(booking.getStatus()).isEqualTo(Booking.BookingStatus.CONFIRMED);
      assertThat(booking.getPaymentTransactionId()).isEqualTo("txn-123");
      assertThat(booking.getConfirmedAt()).isNotNull();
      verify(bookingRepository).save(booking);
    }

    @Test
    void shouldReturnExistingTransactionIdWhenAlreadyConfirmed() {
      booking.setStatus(Booking.BookingStatus.CONFIRMED);
      booking.setPaymentTransactionId("existing-txn");
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));

      String txnId = orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(230), "USD");

      assertThat(txnId).isEqualTo("existing-txn");
      verify(paymentGatewayFactory, never()).resolve(anyString());
      verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenBookingNotFound() {
      when(bookingRepository.findByBookingReferenceWithLock("UNKNOWN"))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> orderService.handlePayment(
          "UNKNOWN", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(230), "USD"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Booking not found");
    }

    @Test
    void shouldThrowWhenUserDoesNotOwnBooking() {
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));

      assertThatThrownBy(() -> orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "other-user",
          BigDecimal.valueOf(230), "USD"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Unauthorized");
    }

    @Test
    void shouldThrowWhenBookingIsCancelled() {
      booking.setStatus(Booking.BookingStatus.CANCELLED);
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));

      assertThatThrownBy(() -> orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(230), "USD"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("cancelled");
    }

    @Test
    void shouldThrowWhenBookingIsExpired() {
      booking.setStatus(Booking.BookingStatus.EXPIRED);
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));

      assertThatThrownBy(() -> orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(230), "USD"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("expired");
    }

    @Test
    void shouldExpireBookingWhenHoldHasExpired() {
      booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));

      assertThatThrownBy(() -> orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(230), "USD"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("hold has expired");

      assertThat(booking.getStatus()).isEqualTo(Booking.BookingStatus.EXPIRED);
      verify(bookingRepository).save(booking);
    }

    @Test
    void shouldThrowWhenAmountMismatch() {
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));

      assertThatThrownBy(() -> orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(100), "USD"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("does not match");
    }

    @Test
    void shouldThrowWhenCurrencyMismatch() {
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));
      booking.setCurrency("EUR");

      assertThatThrownBy(() -> orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(230), "USD"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("does not match booking currency");
    }

    @Test
    void shouldThrowWhenAmountIsNull() {
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));

      assertThatThrownBy(() -> orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          null, "USD"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Payment amount is required");
    }

    @Test
    void shouldThrowWhenCurrencyIsNull() {
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));

      assertThatThrownBy(() -> orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(230), null))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Payment currency is required");
    }

    @Test
    void shouldThrowWhenPaymentDeclined() {
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));
      when(paymentGatewayFactory.resolve("CREDIT_CARD")).thenReturn(paymentGateway);
      when(paymentGateway.charge(any(PaymentChargeRequest.class)))
          .thenReturn(PaymentGatewayResult.failure("Card declined: insufficient funds"));

      assertThatThrownBy(() -> orderService.handlePayment(
          "ABC123", "CREDIT_CARD", "pm_token", "idemp-1", "user-123",
          BigDecimal.valueOf(230), "USD"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("Payment was declined");
    }

    @Test
    void shouldGenerateIdempotencyKeyWhenNotProvided() {
      when(bookingRepository.findByBookingReferenceWithLock("ABC123"))
          .thenReturn(Optional.of(booking));
      when(paymentGatewayFactory.resolve("PAYPAL")).thenReturn(paymentGateway);
      when(paymentGateway.getProviderName()).thenReturn("PAYPAL");
      when(paymentGateway.charge(any(PaymentChargeRequest.class)))
          .thenReturn(PaymentGatewayResult.success("txn-456", "COMPLETED"));

      String txnId = orderService.handlePayment(
          "ABC123", "PAYPAL", "paypal_order_id", null, "user-123",
          BigDecimal.valueOf(230), "USD");

      assertThat(txnId).isEqualTo("txn-456");

      verify(paymentGateway).charge(argThat(req ->
          req.getIdempotencyKey().equals("ABC123:PAYPAL")
      ));
    }
  }
}