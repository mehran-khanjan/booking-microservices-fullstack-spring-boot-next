package com.example.apigatewayservice.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** End-to-end smoke tests – black‑box testing of the api‑gateway‑service. */
@Tag("e2e")
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"apigw.locale.supported-languages=en"})
@ActiveProfiles("test")
class ApiGatewayE2ETest {

  @LocalServerPort private int port;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  /**
   * Performs an HTTP request and returns a {@link ResponseEntity} without throwing on error status
   * codes (4xx/5xx), exactly like {@code TestRestTemplate}.
   */
  private ResponseEntity<String> exchange(
      HttpMethod method, String uri, Consumer<HttpHeaders> headersConsumer) {
    return restClient
        .method(method)
        .uri(uri)
        .headers(headersConsumer)
        .exchange(
            (req, res) -> {
              String body =
                  res.getBody() == null
                      ? null
                      : new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
              return ResponseEntity.status(res.getStatusCode())
                  .headers(res.getHeaders())
                  .body(body);
            });
  }

  @Test
  @DisplayName("client-supplied correlation id is honored end-to-end and echoed back")
  void clientSuppliedCorrelationIdIsHonored() {
    ResponseEntity<String> response =
        exchange(
            HttpMethod.GET,
            "/unmapped/path",
            headers -> {
              headers.add("Accept-Language", "en-US");
              headers.add("X-Correlation-ID", "client-generated-correlation-id");
            });

    // Even though the path is not found (404), the gateway should have added tracing headers.
    assertThat(response.getHeaders().getFirst("X-Correlation-ID"))
        .isEqualTo("client-generated-correlation-id");
    assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
  }

  @Test
  @DisplayName(
      "a well-formed request without validation headers is rejected before reaching routing")
  void malformedRequestNeverReachesRouting() {
    ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/auth/login", headers -> {});

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Accept-Language");
  }

  @Test
  @DisplayName(
      "a fully valid request reaches the load-balanced route and fails downstream, not at the gateway")
  void validRequestReachesRoutingLayer() {
    ResponseEntity<String> response =
        exchange(
            HttpMethod.POST,
            "/api/v1/auth/login",
            headers -> {
              headers.add("Accept-Language", "en-US");
              headers.add("Idempotency-Key", "550e8400-e29b-41d4-a716-446655440000");
            });

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getFirst("X-Correlation-ID")).isNotBlank();
  }

  @Test
  @DisplayName("GET requests never require an Idempotency-Key even on gateway-routed paths")
  void getRequestsSkipIdempotencyOnRoutedPaths() {
    ResponseEntity<String> response =
        exchange(
            HttpMethod.GET,
            "/api/v1/auth/profile",
            headers -> headers.add("Accept-Language", "en-US"));

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }
}
