package com.example.communicationservice.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.commonlib.route.ApiRoutes;
import com.example.communicationservice.config.TestSecurityConfig;
import com.example.communicationservice.service.DlqReplayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context integration test: real Spring MVC dispatch, real Spring Security filter chain
 * (JWT resource server, {@code @PreAuthorize} method security, CORS). Only the DLQ replay service
 * is mocked; everything else - JWT parsing, role extraction, authorization decisions - runs for
 * real against the fixed test tokens provided by {@link TestSecurityConfig}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class DlqAdminControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DlqReplayService dlqReplayService;

  @Test
  void replayEmailDlq_withAdminToken_returns200() throws Exception {
    when(dlqReplayService.replay(
            eq(CommunicationRoutingKeys.DLQ_OTP_EMAIL), eq(CommunicationRoutingKeys.QUEUE_OTP_EMAIL)))
        .thenReturn(3);

    mockMvc
        .perform(
            post(ApiRoutes.Communication.ADMIN_DQL_EMAIL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurityConfig.ADMIN_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").value("3 message(s) replayed from email DLQ"));
  }

  @Test
  void replayEmailDlq_withUserToken_returns403() throws Exception {
    mockMvc
        .perform(
            post(ApiRoutes.Communication.ADMIN_DQL_EMAIL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurityConfig.USER_TOKEN))
        .andExpect(status().isForbidden());
  }

  @Test
  void replayEmailDlq_withoutToken_returns401() throws Exception {
    mockMvc.perform(post(ApiRoutes.Communication.ADMIN_DQL_EMAIL)).andExpect(status().isUnauthorized());
  }

  @Test
  void replaySmsDlq_withAdminToken_returns200() throws Exception {
    when(dlqReplayService.replay(
            eq(CommunicationRoutingKeys.DLQ_OTP_SMS), eq(CommunicationRoutingKeys.QUEUE_OTP_SMS)))
        .thenReturn(1);

    mockMvc
        .perform(
            post(ApiRoutes.Communication.ADMIN_DQL_SMS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurityConfig.ADMIN_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("1 message(s) replayed from SMS DLQ"));
  }

  @Test
  void replaySmsDlq_withUserToken_returns403() throws Exception {
    mockMvc
        .perform(
            post(ApiRoutes.Communication.ADMIN_DQL_SMS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurityConfig.USER_TOKEN))
        .andExpect(status().isForbidden());
  }

  @Test
  void replayEmailDlq_invalidToken_returns403() throws Exception {
    mockMvc
        .perform(
            post(ApiRoutes.Communication.ADMIN_DQL_EMAIL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer garbage-token"))
        .andExpect(status().isForbidden());
  }
}
