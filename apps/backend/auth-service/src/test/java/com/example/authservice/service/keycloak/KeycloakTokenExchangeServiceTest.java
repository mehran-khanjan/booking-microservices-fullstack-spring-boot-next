package com.example.authservice.service.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authservice.dto.signin.TokenResponse;
import com.example.authservice.feign.KeycloakAuthClient;
import com.example.commonlib.exception.AuthenticationException;
import com.example.commonlib.exception.KeycloakOperationException;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;

@ExtendWith(MockitoExtension.class)
class KeycloakTokenExchangeServiceTest {

  private static final String REALM = "booking-app-realm";
  private static final String CLIENT_ID = "auth-service";
  private static final String CLIENT_SECRET = "secret";

  @Mock private KeycloakAuthClient keycloakAuthClient;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private KeycloakTokenExchangeService service;

  @BeforeEach
  void setUp() {
    service = new KeycloakTokenExchangeService(keycloakAuthClient, redisTemplate);
    ReflectionTestUtils.setField(service, "realm", REALM);
    ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);
    ReflectionTestUtils.setField(service, "clientSecret", CLIENT_SECRET);
  }

  @Test
  void exchangeTokenForUser_noCachedServiceToken_fetchesOne_thenExchanges() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("kc:service-token")).thenReturn(null);
    when(keycloakAuthClient.getToken(eq(REALM), argThat(isClientCredentialsForm())))
        .thenReturn(
            Map.of("access_token", "service-token", "refresh_token", "rt", "expires_in", 300));
    when(keycloakAuthClient.getToken(eq(REALM), argThat(isTokenExchangeForm())))
        .thenReturn(
            Map.of("access_token", "user-at", "refresh_token", "user-rt", "expires_in", 300));

    TokenResponse result = service.exchangeTokenForUser("user-id");

    assertThat(result.accessToken()).isEqualTo("user-at");
    verify(valueOperations).set(eq("kc:service-token"), eq("service-token"), any(Duration.class));
  }

  @Test
  void exchangeTokenForUser_cachedServiceToken_skipsFetchingNewOne() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("kc:service-token")).thenReturn("cached-service-token");
    when(keycloakAuthClient.getToken(eq(REALM), argThat(isTokenExchangeForm())))
        .thenReturn(
            Map.of("access_token", "user-at", "refresh_token", "user-rt", "expires_in", 300));

    TokenResponse result = service.exchangeTokenForUser("user-id");

    assertThat(result.accessToken()).isEqualTo("user-at");
    verify(keycloakAuthClient, never()).getToken(eq(REALM), argThat(isClientCredentialsForm()));
  }

  @Test
  void exchangeTokenForUser_unauthorized_throwsAuthenticationException() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("kc:service-token")).thenReturn("cached-service-token");
    when(keycloakAuthClient.getToken(eq(REALM), any())).thenThrow(unauthorized());

    assertThatThrownBy(() -> service.exchangeTokenForUser("user-id"))
        .isInstanceOf(AuthenticationException.class);
  }

  @Test
  void exchangeTokenForUser_keycloakDown_throwsKeycloakOperationException() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("kc:service-token")).thenReturn(null);
    when(keycloakAuthClient.getToken(eq(REALM), any())).thenThrow(serverError());

    assertThatThrownBy(() -> service.exchangeTokenForUser("user-id"))
        .isInstanceOf(KeycloakOperationException.class);
  }

  private org.mockito.ArgumentMatcher<MultiValueMap<String, String>> isClientCredentialsForm() {
    return form -> form != null && "client_credentials".equals(form.getFirst("grant_type"));
  }

  private org.mockito.ArgumentMatcher<MultiValueMap<String, String>> isTokenExchangeForm() {
    return form ->
        form != null
            && "urn:ietf:params:oauth:grant-type:token-exchange"
                .equals(form.getFirst("grant_type"));
  }

  private FeignException.Unauthorized unauthorized() {
    Request request =
        Request.create(
            HttpMethod.POST, "url", Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    return new FeignException.Unauthorized("unauthorized", request, null, Map.of());
  }

  private FeignException serverError() {
    Request request =
        Request.create(
            HttpMethod.POST, "url", Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    return FeignException.errorStatus(
        "getToken",
        feign.Response.builder()
            .status(503)
            .reason("Service Unavailable")
            .request(request)
            .headers(Map.of())
            .build());
  }
}
