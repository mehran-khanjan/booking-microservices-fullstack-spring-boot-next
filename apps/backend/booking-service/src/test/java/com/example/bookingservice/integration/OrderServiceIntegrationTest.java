package com.example.bookingservice.integration;

import com.example.bookingservice.TestBookingServiceApplication;
import com.example.bookingservice.config.TestConfig;
import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.exception.BusinessException;
import com.example.bookingservice.repository.BookingRepository;
import com.example.bookingservice.servcie.OrderService;
import com.example.bookingservice.servcie.payment.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = TestBookingServiceApplication.class)
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
class OrderServiceIntegrationTest {

  @Autowired private BookingRepository bookingRepository;
  @Autowired private OrderService orderService;

  @MockitoBean private PaymentGatewayFactory paymentGatewayFactory;
  @MockitoBean(name = "stripePaymentGatewayService") private PaymentGatewayService paymentGateway;

  private Booking booking;

  @BeforeEach
  void setUp() {
    bookingRepository.deleteAll();

    booking = new Booking();
    booking.setBookingReference("INTEG1");
    booking.setUserId("user-123");
    booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
    booking.setTotalAmount(BigDecimal.valueOf(230));
    booking.setCurrency("USD");
    booking.setContactEmail("test@example.com");
    booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(15));
    bookingRepository.save(booking);
  }

  @Test
  void shouldConfirmBookingAfterSuccessfulPayment() {
    when(paymentGatewayFactory.resolve("STRIPE")).thenReturn(paymentGateway);
    when(paymentGateway.getProviderName()).thenReturn("STRIPE");
    when(paymentGateway.charge(any(PaymentChargeRequest.class)))
        .thenReturn(PaymentGatewayResult.success("txn-integ-1", "succeeded"));

    String txnId = orderService.handlePayment(
        "INTEG1", "STRIPE", "pm_token", "idemp-1", "user-123",
        BigDecimal.valueOf(230), "USD");

    assertThat(txnId).isEqualTo("txn-integ-1");

    Booking updated = bookingRepository.findByBookingReference("INTEG1").orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(Booking.BookingStatus.CONFIRMED);
    assertThat(updated.getPaymentTransactionId()).isEqualTo("txn-integ-1");
    assertThat(updated.getConfirmedAt()).isNotNull();
  }

  @Test
  void shouldReturnExistingTransactionIfAlreadyConfirmed() {
    booking.setStatus(Booking.BookingStatus.CONFIRMED);
    booking.setPaymentTransactionId("existing-txn");
    bookingRepository.save(booking);

    String txnId = orderService.handlePayment(
        "INTEG1", "STRIPE", "pm_token", "idemp-1", "user-123",
        BigDecimal.valueOf(230), "USD");

    assertThat(txnId).isEqualTo("existing-txn");
    verify(paymentGatewayFactory, never()).resolve(anyString());
  }

  @Test
  void shouldExpireBookingWhenHoldExpired() {
    booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(5));
    bookingRepository.save(booking);

    assertThatThrownBy(() -> orderService.handlePayment(
        "INTEG1", "STRIPE", "pm_token", "idemp-1", "user-123",
        BigDecimal.valueOf(230), "USD"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("hold has expired");

    Booking updated = bookingRepository.findByBookingReference("INTEG1").orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(Booking.BookingStatus.EXPIRED);
  }

  @Test
  void shouldThrowWhenAmountMismatch() {
    assertThatThrownBy(() -> orderService.handlePayment(
        "INTEG1", "STRIPE", "pm_token", "idemp-1", "user-123",
        BigDecimal.valueOf(100), "USD"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void shouldThrowWhenPaymentDeclined() {
    when(paymentGatewayFactory.resolve("STRIPE")).thenReturn(paymentGateway);
    when(paymentGateway.charge(any(PaymentChargeRequest.class)))
        .thenReturn(PaymentGatewayResult.failure("Card declined"));

    assertThatThrownBy(() -> orderService.handlePayment(
        "INTEG1", "STRIPE", "pm_token", "idemp-1", "user-123",
        BigDecimal.valueOf(230), "USD"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Payment was declined");
  }
}