package com.example.communicationservice.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.commonlib.route.ApiRoutes;
import com.example.communicationservice.service.DlqReplayService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Pure controller-layer unit tests. No Spring context is loaded, so {@code @PreAuthorize} is not
 * enforced here (that is covered at the integration/e2e layers); this class only verifies request
 * mapping, delegation to {@link DlqReplayService}, and response shape.
 */
@ExtendWith(MockitoExtension.class)
class DlqAdminControllerUnitTest {

  @Mock private DlqReplayService dlqReplayService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DlqAdminController controller = new DlqAdminController(dlqReplayService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  private RequestPostProcessor withAdminAuth() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .claim("sub", "admin-1")
        .claim("email", "admin@example.com")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build();
    Authentication auth = new TestingAuthenticationToken(jwt, null);
    return request -> {
      ((MockHttpServletRequest) request).setUserPrincipal(auth);
      return request;
    };
  }

  @Test
  void replayEmailDlq_delegatesToServiceAndReturnsReplayedCount() throws Exception {
    when(dlqReplayService.replay(
            eq(CommunicationRoutingKeys.DLQ_OTP_EMAIL), eq(CommunicationRoutingKeys.QUEUE_OTP_EMAIL)))
        .thenReturn(4);

    mockMvc
        .perform(post(ApiRoutes.Communication.ADMIN_DQL_EMAIL).with(withAdminAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("4 message(s) replayed from email DLQ"));
  }

  @Test
  void replayEmailDlq_trailingSlashRoute_alsoWorks() throws Exception {
    when(dlqReplayService.replay(
            eq(CommunicationRoutingKeys.DLQ_OTP_EMAIL), eq(CommunicationRoutingKeys.QUEUE_OTP_EMAIL)))
        .thenReturn(0);

    mockMvc
        .perform(post(ApiRoutes.Communication.ADMIN_DQL_EMAIL_TS).with(withAdminAuth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("0 message(s) replayed from email DLQ"));
  }

  @Test
  void replaySmsDlq_delegatesToServiceAndReturnsReplayedCount() throws Exception {
    when(dlqReplayService.replay(
            eq(CommunicationRoutingKeys.DLQ_OTP_SMS), eq(CommunicationRoutingKeys.QUEUE_OTP_SMS)))
        .thenReturn(2);

    mockMvc
        .perform(post(ApiRoutes.Communication.ADMIN_DQL_SMS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("2 message(s) replayed from SMS DLQ"));
  }

  @Test
  void replaySmsDlq_trailingSlashRoute_alsoWorks() throws Exception {
    when(dlqReplayService.replay(
            eq(CommunicationRoutingKeys.DLQ_OTP_SMS), eq(CommunicationRoutingKeys.QUEUE_OTP_SMS)))
        .thenReturn(1);

    mockMvc
        .perform(post(ApiRoutes.Communication.ADMIN_DQL_SMS_TS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("1 message(s) replayed from SMS DLQ"));
  }
}