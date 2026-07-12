package com.example.authservice.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.authservice.config.TestSecurityConfig;
import com.example.authservice.dto.signin.TokenResponse;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.keycloak.KeycloakUserAdminService;
import com.example.commonlib.exception.AuthenticationException;
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

  @MockitoBean private AuthService authService;

  private static final TokenResponse SAMPLE_TOKEN =
          TokenResponse.builder().accessToken("at").refreshToken("rt").expiresIn(300).build();

  @Test
  void signUp_isPubliclyAccessible_andReturns201() throws Exception {
    when(authService.registerUser("integration@example.com", "Passw0rd!"))
            .thenReturn("33333333-3333-3333-3333-333333333333");

    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP_EMAIL)
                .contentType("application/json")
                .content("{\"email\":\"integration@example.com\",\"password\":\"Passw0rd!\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.userId").value("33333333-3333-3333-3333-333333333333"));
  }

  @Test
  void signUp_duplicateUser_returns409() throws Exception {
    when(authService.registerUser("dup@example.com", "Passw0rd!"))
            .thenThrow(new UserAlreadyExistsException("User already exists"));

    mockMvc
        .perform(
            post(ApiRoutes.Auth.SIGN_UP_EMAIL)
                .contentType("application/json")
                .content("{\"email\":\"dup@example.com\",\"password\":\"Passw0rd!\"}"))
            .andExpect(status().isConflict());
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

  @Test
  void initiatePhoneSignUp_isPubliclyAccessible_andReturns201() throws Exception {
    when(authService.registerUserWithPhone(eq("+15551234567"), anyString()))
            .thenReturn("55555555-5555-5555-5555-555555555555");

    mockMvc
            .perform(
                    post(ApiRoutes.Auth.SIGN_UP_PHONE)
                            .contentType("application/json")
                            .content("{\"phoneNumber\":\"+15551234567\",\"password\":\"Passw0rd!\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.userId").value("55555555-5555-5555-5555-555555555555"));
  }

  @Test
  void initiatePhoneSignUp_invalidPhoneNumber_returns400() throws Exception {
    mockMvc
            .perform(
                    post(ApiRoutes.Auth.SIGN_UP_PHONE)
                            .contentType("application/json")
                            .content("{\"phoneNumber\":\"not-a-phone\",\"password\":\"Passw0rd!\"}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  void authenticateWithGoogle_blankIdToken_returns400() throws Exception {
    mockMvc
            .perform(
                    post(ApiRoutes.Auth.SIGN_IN_GOOGLE)
                            .contentType("application/json")
                            .content("{\"idToken\":\"\"}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  void refreshToken_withoutCookie_returns401() throws Exception {
    mockMvc.perform(post(ApiRoutes.Auth.AUTH_REFRESH)).andExpect(status().isUnauthorized());
  }

  @Test
  void signOut_withoutCookie_stillReturns200_andClearsCookie() throws Exception {
    mockMvc
            .perform(post(ApiRoutes.Auth.SIGN_OUT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void changePassword_withoutBearerToken_returns401() throws Exception {
    mockMvc
            .perform(
                    post(ApiRoutes.Auth.CHANGE_PASSWORD)
                            .contentType("application/json")
                            .content("{\"oldPassword\":\"old\",\"newPassword\":\"New1!\"}"))
            .andExpect(status().isUnauthorized());
  }

  @Test
  void changePassword_withValidJwt_delegatesToAuthServiceWithSubjectClaim() throws Exception {
    doNothing().when(authService).changePassword("user-sub-123", "old", "New1!");

    mockMvc
            .perform(
                    post(ApiRoutes.Auth.CHANGE_PASSWORD)
                            .with(jwt().jwt(jwt -> jwt.subject("user-sub-123")))
                            .contentType("application/json")
                            .content("{\"oldPassword\":\"old\",\"newPassword\":\"New1!\"}"))
            .andExpect(status().isOk());

    verify(authService).changePassword("user-sub-123", "old", "New1!");
  }
}
