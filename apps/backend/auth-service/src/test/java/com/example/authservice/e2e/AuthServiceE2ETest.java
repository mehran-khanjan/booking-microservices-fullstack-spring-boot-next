package com.example.authservice.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.authservice.config.TestSecurityConfig;
import com.example.authservice.service.KeycloakUserAdminService;
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

  @BeforeEach
  void setUp() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void signUp_success_returns201WithLocationHeaderAndUserId() {
    when(keycloakUserAdminService.createUser(anyString(), any(), anyString(), anyMap()))
        .thenReturn("44444444-4444-4444-4444-444444444444");

    var response =
        restClient
            .post()
            .uri(ApiRoutes.Auth.SIGN_UP)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"e2e@example.com\",\"password\":\"Passw0rd!\"}")
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getFirst("Location"))
        .isEqualTo("/api/v1/users/44444444-4444-4444-4444-444444444444");
    assertThat(response.getBody()).contains("44444444-4444-4444-4444-444444444444");
    assertThat(response.getBody()).contains("\"success\":true");
  }

  @Test
  void signUp_duplicateUser_returns409() {
    when(keycloakUserAdminService.createUser(anyString(), any(), anyString(), anyMap()))
        .thenThrow(new UserAlreadyExistsException("User already exists"));

    assertThatConflictIsReturned();
  }

  private void assertThatConflictIsReturned() {
    try {
      restClient
          .post()
          .uri(ApiRoutes.Auth.SIGN_UP)
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
          .uri(ApiRoutes.Auth.SIGN_UP)
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
}
