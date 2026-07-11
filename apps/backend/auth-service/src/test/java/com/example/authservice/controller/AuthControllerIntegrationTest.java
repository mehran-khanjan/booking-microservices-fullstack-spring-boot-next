package com.example.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authservice.config.TestSecurityConfig;
import com.example.authservice.service.keycloak.KeycloakUserAdminService;
import com.example.commonlib.exception.UserAlreadyExistsException;
import com.example.commonlib.route.ApiRoutes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context integration test: real Spring MVC dispatch, real Spring Security filter chain (CORS,
 * JWT resource server, authorization rules), real bean validation and JSON serialization. Only the
 * outbound call to Keycloak's admin API is mocked, since standing up a real Keycloak instance is
 * out of scope for this module's test suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AuthControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KeycloakUserAdminService keycloakUserAdminService;

  @Test
  void signUp_isPubliclyAccessible_andReturns201() throws Exception {
    when(keycloakUserAdminService.createUser(anyString(), any(), anyString(), anyMap()))
        .thenReturn("33333333-3333-3333-3333-333333333333");

    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP_EMAIL)
                .contentType("application/json")
                .content("{\"email\":\"integration@example.com\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value("33333333-3333-3333-3333-333333333333"));
  }

  @Test
  void signUp_duplicateUser_returns409() throws Exception {
    when(keycloakUserAdminService.createUser(anyString(), any(), anyString(), anyMap()))
        .thenThrow(new UserAlreadyExistsException("User already exists"));

    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP_EMAIL)
                .contentType("application/json")
                .content("{\"email\":\"dup@example.com\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void signUp_invalidBody_returns400() throws Exception {
    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP_EMAIL)
                .contentType("application/json")
                .content("{\"email\":\"invalid\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void protectedRoute_withoutBearerToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/some-protected-resource")).andExpect(status().isUnauthorized());
  }

  @Test
  void corsPreflight_forSignUpRoute_isAllowed() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(
                    ApiRoutes.Auth.SIGN_UP_EMAIL)
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk());
  }
}
