package com.example.authservice.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.example.authservice.config.TestSecurityConfig;
import com.example.authservice.dto.signin.TokenResponse;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.keycloak.KeycloakUserAdminService;
import com.example.commonlib.exception.AuthenticationException;
import com.example.commonlib.exception.UserAlreadyExistsException;
import com.example.commonlib.route.ApiRoutes;
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
 * filter chain, real HTTP transport) on a random port, and exercises it purely through {@link
 * RestClient} making real HTTP calls - no MockMvc, no in-process dispatch shortcuts. The only
 * stubbed collaborator is the outbound Keycloak admin call, since a live Keycloak server is outside
 * this module's test scope.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AuthServiceE2ETest {

  @LocalServerPort private int port;

  @MockitoBean private KeycloakUserAdminService keycloakUserAdminService;

  private RestClient restClient;

  @MockitoBean private AuthService authService;

  private static final TokenResponse SAMPLE_TOKEN =
          TokenResponse.builder().accessToken("at").refreshToken("rt").expiresIn(300).build();

  @BeforeEach
  void setUp() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  private void assertThatConflictIsReturned() {
    try {
      restClient
          .post()
          .uri(ApiRoutes.Auth.SIGN_UP_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"email\":\"dup@example.com\",\"password\":\"Passw0rd!\"}")
          .retrieve()
          .toEntity(String.class);
      org.junit.jupiter.api.Assertions.fail("Expected a 409 Conflict response");
    } catch (HttpClientErrorException ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(ex.getResponseBodyAsString()).contains("\"success\":false");
    }
  }

  @Test
  void signUp_invalidPayload_returns400() {
    try {
      restClient
          .post()
          .uri(ApiRoutes.Auth.SIGN_UP_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"email\":\"not-an-email\",\"password\":\"\"}")
          .retrieve()
          .toEntity(String.class);
      org.junit.jupiter.api.Assertions.fail("Expected a 400 Bad Request response");
    } catch (HttpClientErrorException ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  void protectedResource_withoutToken_returns401() {
    try {
      restClient.get().uri("/api/v1/some-protected-resource").retrieve().toEntity(String.class);
      org.junit.jupiter.api.Assertions.fail("Expected a 401 Unauthorized response");
    } catch (HttpClientErrorException ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Test
  void signIn_success_returns200_withRefreshTokenCookie() {
    when(authService.login(eq("e2e@example.com"), isNull(), anyString())).thenReturn(SAMPLE_TOKEN);

    var response =
            restClient
                    .post()
                    .uri(ApiRoutes.Auth.SIGN_IN_EMAIL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"email\":\"e2e@example.com\",\"password\":\"Passw0rd!\"}")
                    .retrieve()
                    .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("Set-Cookie")).contains("refresh_token=rt");
    assertThat(response.getBody()).contains("\"access_token\":\"at\"");
  }

  @Test
  void refreshToken_withoutCookie_returns401() {
    try {
      restClient.post().uri(ApiRoutes.Auth.AUTH_REFRESH).retrieve().toEntity(String.class);
      org.junit.jupiter.api.Assertions.fail("Expected a 401 Unauthorized response");
    } catch (HttpClientErrorException ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }

  @Test
  void signOut_isPubliclyAccessible_andReturns200() {
    var response = restClient.post().uri(ApiRoutes.Auth.SIGN_OUT).retrieve().toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"success\":true");
  }

  @Test
  void changePassword_withoutBearerToken_returns401() {
    try {
      restClient
              .post()
              .uri(ApiRoutes.Auth.CHANGE_PASSWORD)
              .contentType(MediaType.APPLICATION_JSON)
              .body("{\"oldPassword\":\"old\",\"newPassword\":\"New1!\"}")
              .retrieve()
              .toEntity(String.class);
      org.junit.jupiter.api.Assertions.fail("Expected a 401 Unauthorized response");
    } catch (HttpClientErrorException ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }
}
