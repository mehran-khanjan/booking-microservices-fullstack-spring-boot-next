package com.example.bookingservice.unit;

import com.example.bookingservice.property.StripeProperties;
import com.example.bookingservice.servcie.payment.PaymentChargeRequest;
import com.example.bookingservice.servcie.payment.PaymentGatewayResult;
import com.example.bookingservice.servcie.payment.StripePaymentGatewayService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StripePaymentGatewayServiceTest {

  @Mock private StripeProperties stripeProperties;

  private StripePaymentGatewayService stripeService;

  @BeforeEach
  void setUp() {
    when(stripeProperties.getApiKey()).thenReturn("sk_test_dummy");
    stripeService = new StripePaymentGatewayService(stripeProperties);
  }

  @Test
  void shouldReturnProviderName() {
    assertThat(stripeService.getProviderName()).isEqualTo("STRIPE");
  }

  @Test
  void shouldReturnSuccessWhenPaymentIntentSucceeds() throws StripeException {
    PaymentIntent intent = mock(PaymentIntent.class);
    when(intent.getId()).thenReturn("pi_123");
    when(intent.getStatus()).thenReturn("succeeded");

    try (MockedStatic<PaymentIntent> mockedPaymentIntent = mockStatic(PaymentIntent.class)) {
      mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
          .thenReturn(intent);

      PaymentChargeRequest request = PaymentChargeRequest.builder()
          .bookingReference("ABC123")
          .amount(BigDecimal.valueOf(129.99))
          .currency("USD")
          .paymentToken("pm_123")
          .idempotencyKey("idemp-1")
          .build();

      PaymentGatewayResult result = stripeService.charge(request);

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getTransactionId()).isEqualTo("pi_123");
      assertThat(result.getProviderStatus()).isEqualTo("succeeded");
    }
  }

  @Test
  void shouldReturnFailureWhenPaymentIntentNotSucceeded() throws StripeException {
    PaymentIntent intent = mock(PaymentIntent.class);
    when(intent.getId()).thenReturn("pi_123");
    when(intent.getStatus()).thenReturn("requires_payment_method");

    try (MockedStatic<PaymentIntent> mockedPaymentIntent = mockStatic(PaymentIntent.class)) {
      mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
          .thenReturn(intent);

      PaymentChargeRequest request = PaymentChargeRequest.builder()
          .bookingReference("ABC123")
          .amount(BigDecimal.valueOf(129.99))
          .currency("USD")
          .paymentToken("pm_123")
          .idempotencyKey("idemp-1")
          .build();

      PaymentGatewayResult result = stripeService.charge(request);

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getFailureReason()).contains("not completed");
    }
  }

  @Test
  void shouldHandleCardDeclined() throws StripeException {
    com.stripe.exception.CardException cardException =
        new com.stripe.exception.CardException("declined", "card_declined", null, null, null, null, null, null);

    try (MockedStatic<PaymentIntent> mockedPaymentIntent = mockStatic(PaymentIntent.class)) {
      mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
          .thenThrow(cardException);

      PaymentChargeRequest request = PaymentChargeRequest.builder()
          .bookingReference("ABC123")
          .amount(BigDecimal.valueOf(129.99))
          .currency("USD")
          .paymentToken("pm_123")
          .idempotencyKey("idemp-1")
          .build();

      PaymentGatewayResult result = stripeService.charge(request);

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getFailureReason()).contains("Card declined");
    }
  }

  @Test
  void shouldHandleStripeException() throws StripeException {
    StripeException stripeException = mock(StripeException.class);
    when(stripeException.getMessage()).thenReturn("API error");

    try (MockedStatic<PaymentIntent> mockedPaymentIntent = mockStatic(PaymentIntent.class)) {
      mockedPaymentIntent.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
          .thenThrow(stripeException);

      PaymentChargeRequest request = PaymentChargeRequest.builder()
          .bookingReference("ABC123")
          .amount(BigDecimal.valueOf(129.99))
          .currency("USD")
          .paymentToken("pm_123")
          .idempotencyKey("idemp-1")
          .build();

      PaymentGatewayResult result = stripeService.charge(request);

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getFailureReason()).contains("try again");
    }
  }

  @Test
  void shouldHandleZeroDecimalCurrency() throws StripeException {
    PaymentIntent intent = mock(PaymentIntent.class);
    when(intent.getId()).thenReturn("pi_456");
    when(intent.getStatus()).thenReturn("succeeded");

    try (MockedStatic<PaymentIntent> mockedPaymentIntent = mockStatic(PaymentIntent.class)) {
      ArgumentCaptor<PaymentIntentCreateParams> paramsCaptor =
          ArgumentCaptor.forClass(PaymentIntentCreateParams.class);

      mockedPaymentIntent.when(() -> PaymentIntent.create(paramsCaptor.capture(), any()))
          .thenReturn(intent);

      PaymentChargeRequest request = PaymentChargeRequest.builder()
          .bookingReference("ABC123")
          .amount(BigDecimal.valueOf(1000))
          .currency("JPY")
          .paymentToken("pm_123")
          .idempotencyKey("idemp-1")
          .build();

      PaymentGatewayResult result = stripeService.charge(request);

      assertThat(result.isSuccess()).isTrue();
      assertThat(paramsCaptor.getValue().getAmount()).isEqualTo(1000L);
    }
  }
}