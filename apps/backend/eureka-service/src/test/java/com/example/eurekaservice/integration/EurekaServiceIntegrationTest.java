package com.example.eurekaservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eurekaservice.EurekaServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    classes = EurekaServiceApplication.class, // explicitly point to the main class
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "eureka.client.register-with-eureka=false",
      "eureka.client.fetch-registry=false",
      "eureka.server.enable-self-preservation=false"
    })
class EurekaServiceIntegrationTest {

  @LocalServerPort private int port;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = RestClient.create("http://localhost:" + port);
  }

  @Test
  void eurekaDashboardReturnsHtml() {
    String response = restClient.get().uri("/").retrieve().body(String.class);
    assertThat(response).contains("Eureka");
  }

  @Test
  void eurekaAppsEndpointReturnsXml() {
    ResponseEntity<String> response =
        restClient.get().uri("/eureka/apps").retrieve().toEntity(String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString()).contains("xml");
    assertThat(response.getBody()).contains("<applications>");
  }

  @Test
  void healthEndpointIsUp() {
    String response = restClient.get().uri("/actuator/health").retrieve().body(String.class);
    assertThat(response).contains("\"status\":\"UP\"");
  }
}
