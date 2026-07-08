package com.example.apigatewayservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** Integration tests that boot the full context with a test‑only controller. */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"apigw.locale.supported-languages=en"})
@ActiveProfiles("test")
class GatewayEdgeFilterIntegrationTest {

  @LocalServerPort private int port;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

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

  @TestConfiguration
  static class EchoControllerConfig {
    @Bean
    EchoController echoController() {
      return new EchoController();
    }
  }

  @RestController
  static class EchoController {
    @GetMapping("/it/echo")
    public String get(@RequestHeader(value = "Accept-Language", required = false) String lang) {
      return "ok:" + lang;
    }

    @PostMapping("/it/echo")
    public String post(@RequestHeader(value = "Idempotency-Key", required = false) String key) {
      return "ok:" + key;
    }
  }

  @Test
  @DisplayName("GET without Accept-Language is rejected with 400 and a JSON error body")
  void getWithoutAcceptLanguageReturns400() {
    ResponseEntity<String> response = exchange(HttpMethod.GET, "/it/echo", headers -> {});
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Accept-Language");
  }

  @Test
  @DisplayName(
      "GET with valid Accept-Language reaches the downstream handler and echoes tracing headers")
  void getWithValidAcceptLanguageSucceeds() {
    ResponseEntity<String> response =
        exchange(HttpMethod.GET, "/it/echo", headers -> headers.add("Accept-Language", "en-US"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok:en-US");
    assertThat(response.getHeaders().getFirst("X-Correlation-ID")).isNotBlank();
    assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
    assertThat(response.getHeaders().getFirst("Content-Language")).isEqualTo("en-US");
  }

  @Test
  @DisplayName("POST without Idempotency-Key is rejected with 400")
  void postWithoutIdempotencyKeyReturns400() {
    ResponseEntity<String> response =
        exchange(HttpMethod.POST, "/it/echo", headers -> headers.add("Accept-Language", "en-US"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Idempotency-Key");
  }

  @Test
  @DisplayName("POST with a valid Idempotency-Key and Accept-Language succeeds")
  void postWithValidHeadersSucceeds() {
    ResponseEntity<String> response =
        exchange(
            HttpMethod.POST,
            "/it/echo",
            headers -> {
              headers.add("Accept-Language", "en-US");
              headers.add("Idempotency-Key", "550e8400-e29b-41d4-a716-446655440000");
            });
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("ok:550e8400-e29b-41d4-a716-446655440000");
  }

  @Test
  @DisplayName("POST with a malformed Idempotency-Key is rejected with 400")
  void postWithMalformedIdempotencyKeyReturns400() {
    ResponseEntity<String> response =
        exchange(
            HttpMethod.POST,
            "/it/echo",
            headers -> {
              headers.add("Accept-Language", "en-US");
              headers.add("Idempotency-Key", "not-a-real-uuid");
            });
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("actuator health endpoint bypasses locale/idempotency validation")
  void actuatorHealthBypassesValidation() {
    ResponseEntity<String> response = exchange(HttpMethod.GET, "/actuator/health", headers -> {});
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }
}
