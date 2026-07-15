package com.example.bookingservice.unit;

import com.example.bookingservice.exception.BusinessException;
import com.example.bookingservice.servcie.payment.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentGatewayFactoryTest {

  private PaymentGatewayFactory factory;
  private PaymentGatewayService stripe;
  private PaymentGatewayService paypal;

  @BeforeEach
  void setUp() {
    stripe = mock(PaymentGatewayService.class);
    when(stripe.getProviderName()).thenReturn("STRIPE");

    paypal = mock(PaymentGatewayService.class);
    when(paypal.getProviderName()).thenReturn("PAYPAL");

    factory = new PaymentGatewayFactory(List.of(stripe, paypal));
  }

  @Test
  void shouldResolveStripeForCreditCard() {
    assertThat(factory.resolve("CREDIT_CARD")).isEqualTo(stripe);
  }

  @Test
  void shouldResolveStripeForDebitCard() {
    assertThat(factory.resolve("DEBIT_CARD")).isEqualTo(stripe);
  }

  @Test
  void shouldResolveStripeForCard() {
    assertThat(factory.resolve("CARD")).isEqualTo(stripe);
  }

  @Test
  void shouldResolveStripeForStripe() {
    assertThat(factory.resolve("STRIPE")).isEqualTo(stripe);
  }

  @Test
  void shouldResolvePayPal() {
    assertThat(factory.resolve("PAYPAL")).isEqualTo(paypal);
  }

  @Test
  void shouldThrowForUnsupportedMethod() {
    assertThatThrownBy(() -> factory.resolve("BITCOIN"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Unsupported payment method");
  }

  @Test
  void shouldThrowForNullMethod() {
    assertThatThrownBy(() -> factory.resolve(null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Payment method is required");
  }

  @Test
  void shouldThrowForBlankMethod() {
    assertThatThrownBy(() -> factory.resolve("  "))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Payment method is required");
  }

  @Test
  void shouldResolveCaseInsensitive() {
    assertThat(factory.resolve("paypal")).isEqualTo(paypal);
    assertThat(factory.resolve("Stripe")).isEqualTo(stripe);
  }
}