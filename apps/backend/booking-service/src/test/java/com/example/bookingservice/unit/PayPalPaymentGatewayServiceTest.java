package com.example.bookingservice.unit;

import com.example.bookingservice.property.PayPalProperties;
import com.example.bookingservice.servcie.payment.PaymentChargeRequest;
import com.example.bookingservice.servcie.payment.PaymentGatewayResult;
import com.example.bookingservice.servcie.payment.PayPalPaymentGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayPalPaymentGatewayServiceTest {

  @Mock private PayPalProperties payPalProperties;
  @Mock private RestTemplateBuilder restTemplateBuilder;
  @Mock private RestTemplate restTemplate;

  private PayPalPaymentGatewayService payPalService;

  @BeforeEach
  void setUp() {
    when(payPalProperties.getClientId()).thenReturn("dummy_client");
    when(payPalProperties.getClientSecret()).thenReturn("dummy_secret");
    when(payPalProperties.getBaseUrl()).thenReturn("https://api-m.sandbox.paypal.com");
    when(restTemplateBuilder.connectTimeout(Duration.ofSeconds(5))).thenReturn(restTemplateBuilder);
    when(restTemplateBuilder.readTimeout(Duration.ofSeconds(10))).thenReturn(restTemplateBuilder);
    when(restTemplateBuilder.build()).thenReturn(restTemplate);

    payPalService = new PayPalPaymentGatewayService(payPalProperties, restTemplateBuilder);
  }

  @Test
  void shouldReturnProviderName() {
    assertThat(payPalService.getProviderName()).isEqualTo("PAYPAL");
  }

  @Test
  void shouldCaptureOrderSuccessfully() {
    ResponseEntity<Map<String, Object>> tokenResponse = ResponseEntity.ok(
        Map.of("access_token", "access-token-123")
    );

    ResponseEntity<Map<String, Object>> captureResponse = ResponseEntity.ok(
        Map.of(
            "id", "ORDER123",
            "status", "COMPLETED",
            "purchase_units", List.of(
                Map.of("payments", Map.of(
                    "captures", List.of(Map.of("id", "CAPTURE123"))
                ))
            )
        )
    );

    when(restTemplate.exchange(
        eq("https://api-m.sandbox.paypal.com/v1/oauth2/token"),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        any(ParameterizedTypeReference.class)
    )).thenReturn(tokenResponse);

    when(restTemplate.exchange(
        eq("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORDER_ID/capture"),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        any(ParameterizedTypeReference.class)
    )).thenReturn(captureResponse);

    PaymentChargeRequest request = PaymentChargeRequest.builder()
        .bookingReference("ABC123")
        .amount(BigDecimal.valueOf(129.99))
        .currency("USD")
        .paymentToken("ORDER_ID")
        .idempotencyKey("idemp-1")
        .build();

    PaymentGatewayResult result = payPalService.charge(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getTransactionId()).isEqualTo("CAPTURE123");
  }

  @Test
  void shouldReturnFailureWhenOrderNotCompleted() {
    ResponseEntity<Map<String, Object>> tokenResponse = ResponseEntity.ok(
        Map.of("access_token", "access-token-123")
    );

    ResponseEntity<Map<String, Object>> captureResponse = ResponseEntity.ok(
        Map.of("id", "ORDER123", "status", "PENDING")
    );

    when(restTemplate.exchange(
        eq("https://api-m.sandbox.paypal.com/v1/oauth2/token"),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        any(ParameterizedTypeReference.class)
    )).thenReturn(tokenResponse);

    when(restTemplate.exchange(
        eq("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORDER_ID/capture"),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        any(ParameterizedTypeReference.class)
    )).thenReturn(captureResponse);

    PaymentChargeRequest request = PaymentChargeRequest.builder()
        .bookingReference("ABC123")
        .amount(BigDecimal.valueOf(129.99))
        .currency("USD")
        .paymentToken("ORDER_ID")
        .idempotencyKey("idemp-1")
        .build();

    PaymentGatewayResult result = payPalService.charge(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getFailureReason()).contains("not completed");
  }

  @Test
  void shouldHandleRestClientException() {
    when(restTemplate.exchange(
        eq("https://api-m.sandbox.paypal.com/v1/oauth2/token"),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        any(ParameterizedTypeReference.class)
    )).thenThrow(new RestClientException("Connection refused"));

    PaymentChargeRequest request = PaymentChargeRequest.builder()
        .bookingReference("ABC123")
        .amount(BigDecimal.valueOf(129.99))
        .currency("USD")
        .paymentToken("ORDER_ID")
        .idempotencyKey("idemp-1")
        .build();

    PaymentGatewayResult result = payPalService.charge(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getFailureReason()).contains("try again");
  }

  @Test
  void shouldHandleEmptyResponseBody() {
    ResponseEntity<Map<String, Object>> tokenResponse = ResponseEntity.ok(
        Map.of("access_token", "access-token-123")
    );

    ResponseEntity<Map<String, Object>> captureResponse = new ResponseEntity<>(HttpStatus.OK);

    when(restTemplate.exchange(
        eq("https://api-m.sandbox.paypal.com/v1/oauth2/token"),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        any(ParameterizedTypeReference.class)
    )).thenReturn(tokenResponse);

    when(restTemplate.exchange(
        eq("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORDER_ID/capture"),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        any(ParameterizedTypeReference.class)
    )).thenReturn(captureResponse);

    PaymentChargeRequest request = PaymentChargeRequest.builder()
        .bookingReference("ABC123")
        .amount(BigDecimal.valueOf(129.99))
        .currency("USD")
        .paymentToken("ORDER_ID")
        .idempotencyKey("idemp-1")
        .build();

    PaymentGatewayResult result = payPalService.charge(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getFailureReason()).contains("Empty response");
  }

  @Test
  void shouldFallbackToOrderIdWhenCaptureIdCannotBeExtracted() {
    ResponseEntity<Map<String, Object>> tokenResponse = ResponseEntity.ok(
        Map.of("access_token", "access-token-123")
    );

    ResponseEntity<Map<String, Object>> captureResponse = ResponseEntity.ok(
        Map.of("id", "ORDER123", "status", "COMPLETED")
    );

    when(restTemplate.exchange(
        eq("https://api-m.sandbox.paypal.com/v1/oauth2/token"),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        any(ParameterizedTypeReference.class)
    )).thenReturn(tokenResponse);

    when(restTemplate.exchange(
        eq("https://api-m.sandbox.paypal.com/v2/checkout/orders/ORDER_ID/capture"),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        any(ParameterizedTypeReference.class)
    )).thenReturn(captureResponse);

    PaymentChargeRequest request = PaymentChargeRequest.builder()
        .bookingReference("ABC123")
        .amount(BigDecimal.valueOf(129.99))
        .currency("USD")
        .paymentToken("ORDER_ID")
        .idempotencyKey("idemp-1")
        .build();

    PaymentGatewayResult result = payPalService.charge(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getTransactionId()).isEqualTo("ORDER123");
  }
}