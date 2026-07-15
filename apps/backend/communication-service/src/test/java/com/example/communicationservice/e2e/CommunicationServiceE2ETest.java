package com.example.communicationservice.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.commonlib.route.ApiRoutes;
import com.example.communicationservice.config.TestSecurityConfig;
import com.example.communicationservice.service.DlqReplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * True end-to-end test: boots the whole Spring context (embedded servlet container, real security
 * filter chain, real HTTP transport) on a random port, and exercises it purely through
 * {@link RestClient} making real HTTP calls - no MockMvc, no in-process dispatch shortcuts. Only
 * the DLQ replay service is stubbed; JWT parsing and {@code @PreAuthorize} authorization run for
 * real against the fixed test tokens provided by {@link TestSecurityConfig}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class CommunicationServiceE2ETest {

  @LocalServerPort private int port;

  @MockitoBean private DlqReplayService dlqReplayService;

  private RestClient restClient;

  @BeforeEach
  void setUp() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void replayEmailDlq_withAdminToken_returns200AndReplayedCount() {
    when(dlqReplayService.replay(
            eq(CommunicationRoutingKeys.DLQ_OTP_EMAIL), eq(CommunicationRoutingKeys.QUEUE_OTP_EMAIL)))
        .thenReturn(5);

    var response =
        restClient
            .post()
            .uri(ApiRoutes.Communication.ADMIN_DQL_EMAIL)
            .header("Authorization", "Bearer " + TestSecurityConfig.ADMIN_TOKEN)
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("5 message(s) replayed from email DLQ");
    assertThat(response.getBody()).contains("\"success\":true");
  }

  @Test
  void replaySmsDlq_withAdminToken_returns200AndReplayedCount() {
    when(dlqReplayService.replay(
            eq(CommunicationRoutingKeys.DLQ_OTP_SMS), eq(CommunicationRoutingKeys.QUEUE_OTP_SMS)))
        .thenReturn(2);

    var response =
        restClient
            .post()
            .uri(ApiRoutes.Communication.ADMIN_DQL_SMS)
            .header("Authorization", "Bearer " + TestSecurityConfig.ADMIN_TOKEN)
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("2 message(s) replayed from SMS DLQ");
  }

  @Test
  void replayEmailDlq_withNonAdminToken_returns403() {
    try {
      restClient
          .post()
          .uri(ApiRoutes.Communication.ADMIN_DQL_EMAIL)
          .header("Authorization", "Bearer " + TestSecurityConfig.USER_TOKEN)
          .retrieve()
          .toEntity(String.class);
      org.junit.jupiter.api.Assertions.fail("Expected a 403 Forbidden response");
    } catch (HttpClientErrorException ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  void replayEmailDlq_withoutToken_returns401() {
    try {
      restClient.post().uri(ApiRoutes.Communication.ADMIN_DQL_EMAIL).retrieve().toEntity(String.class);
      org.junit.jupiter.api.Assertions.fail("Expected a 401 Unauthorized response");
    } catch (HttpClientErrorException ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Test
  void unmappedProtectedResource_returns401WithoutToken() {
    try {
      restClient.get().uri("/api/v1/communication/does-not-exist").retrieve().toEntity(String.class);
      org.junit.jupiter.api.Assertions.fail("Expected a 401 Unauthorized response");
    } catch (HttpClientErrorException ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }
}
