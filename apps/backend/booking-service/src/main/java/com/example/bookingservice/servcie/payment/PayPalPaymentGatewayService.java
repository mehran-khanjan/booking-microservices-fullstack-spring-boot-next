package com.example.bookingservice.servcie.payment;

import com.example.bookingservice.property.PayPalProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Captures PayPal orders through PayPal's Orders v2 REST API.
 *
 * <p>{@code paymentToken} is expected to be a PayPal Order ID that the frontend already created and
 * had the buyer approve via the PayPal JS SDK (standard PayPal Checkout flow: create order
 * client-side -> buyer approves -> server captures). This service performs only the server-side
 * capture step, which is the point at which funds actually move.
 *
 * <p>No PayPal-specific SDK dependency is required - this uses the documented, stable REST
 * endpoints ({@code POST /v1/oauth2/token}, {@code POST /v2/checkout/orders/{id}/capture}) via
 * Spring's {@code RestTemplate}, which avoids coupling to a particular checkout-sdk version.
 */
@Service
@Slf4j
public class PayPalPaymentGatewayService implements PaymentGatewayService {

  private static final String PROVIDER_NAME = "PAYPAL";

  private final PayPalProperties properties;
  private final RestTemplate restTemplate;

  public PayPalPaymentGatewayService(PayPalProperties properties, RestTemplateBuilder builder) {
    this.properties = properties;
    this.restTemplate =
        builder.connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(10)).build();
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  /**
   * Charges the customer by capturing an already approved PayPal order.
   *
   * @param request the charge request containing the PayPal Order ID
   * @return the gateway result with capture ID or failure reason
   */
  @Override
  public PaymentGatewayResult charge(PaymentChargeRequest request) {
    try {
      String accessToken = fetchAccessToken();
      return captureOrder(request, accessToken);
    } catch (RestClientException e) {
      log.error("PayPal payment failed for booking {}", request.getBookingReference(), e);
      return PaymentGatewayResult.failure("Payment gateway error, please try again");
    }
  }

  /**
   * Fetches a new OAuth2 access token from PayPal.
   *
   * @return the access token string
   * @throws IllegalStateException if no token is returned
   */
  private String fetchAccessToken() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "client_credentials");

    HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            properties.getBaseUrl() + "/v1/oauth2/token",
            HttpMethod.POST,
            entity,
            new ParameterizedTypeReference<>() {});

    Map<String, Object> tokenBody = response.getBody();
    if (tokenBody == null || tokenBody.get("access_token") == null) {
      throw new IllegalStateException("PayPal did not return an access token");
    }
    return (String) tokenBody.get("access_token");
  }

  /**
   * Captures a PayPal order using its order ID and an access token.
   *
   * @param request the charge request
   * @param accessToken the OAuth2 token
   * @return the gateway result
   */
  private PaymentGatewayResult captureOrder(PaymentChargeRequest request, String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add("PayPal-Request-Id", request.getIdempotencyKey());

    HttpEntity<Void> entity = new HttpEntity<>(headers);
    String url =
        properties.getBaseUrl() + "/v2/checkout/orders/" + request.getPaymentToken() + "/capture";

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

    Map<String, Object> orderBody = response.getBody();
    if (orderBody == null) {
      return PaymentGatewayResult.failure("Empty response from PayPal");
    }

    String status = (String) orderBody.get("status");
    if (!"COMPLETED".equals(status)) {
      log.warn(
          "PayPal order {} for booking {} not completed, status={}",
          request.getPaymentToken(),
          request.getBookingReference(),
          status);
      return PaymentGatewayResult.failure("PayPal order not completed: " + status);
    }

    String captureId = extractCaptureId(orderBody);
    log.info(
        "PayPal order {} captured ({}) for booking {}",
        request.getPaymentToken(),
        captureId,
        request.getBookingReference());
    return PaymentGatewayResult.success(captureId, status);
  }

  @SuppressWarnings("unchecked")
  private String extractCaptureId(Map<String, Object> orderBody) {
    try {
      List<Map<String, Object>> purchaseUnits =
          (List<Map<String, Object>>) orderBody.get("purchase_units");
      Map<String, Object> payments = (Map<String, Object>) purchaseUnits.get(0).get("payments");
      List<Map<String, Object>> captures = (List<Map<String, Object>>) payments.get("captures");
      return (String) captures.get(0).get("id");
    } catch (Exception e) {
      log.warn("Could not extract PayPal capture id from response, falling back to order id");
      return (String) orderBody.get("id");
    }
  }
}
