package com.example.authservice.service.keycloak;

import com.example.authservice.dto.signin.TokenResponse;
import com.example.authservice.feign.KeycloakAuthClient;
import com.example.commonlib.exception.AuthenticationException;
import com.example.commonlib.exception.KeycloakOperationException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Service responsible for exchanging tokens with Keycloak, specifically for token exchange (acting
 * on behalf of a user) and obtaining service account tokens.
 *
 * <p>This service uses Keycloak's OAuth2 Token Exchange grant type (<a
 * href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693</a>) to obtain an access token for
 * a specific user, given a service account token. It also manages caching of the service account
 * token in Redis to reduce round trips to Keycloak.
 *
 * <p>The service is protected with resilience patterns:
 *
 * <ul>
 *   <li>{@link CircuitBreaker} to prevent cascading failures when Keycloak is unavailable
 *   <li>{@link Retry} to transient network issues
 * </ul>
 *
 * <p>Configuration is read from application properties:
 *
 * <ul>
 *   <li>{@code keycloak.realm} – the realm name
 *   <li>{@code keycloak.client-id} – the OAuth2 client ID
 *   <li>{@code keycloak.client-secret} – the OAuth2 client secret
 * </ul>
 *
 * @author Your Team
 * @see KeycloakAuthClient
 * @see TokenResponse
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakTokenExchangeService {

  /** Feign client for calling Keycloak endpoints. */
  private final KeycloakAuthClient keycloakAuthClient;

  /** Redis template for caching the service account token. */
  private final StringRedisTemplate redisTemplate;

  /** Keycloak realm name from application properties. */
  @Value("${keycloak.realm}")
  private String realm;

  /** OAuth2 client ID from application properties. */
  @Value("${keycloak.client-id}")
  private String clientId;

  /** OAuth2 client secret from application properties. */
  @Value("${keycloak.client-secret}")
  private String clientSecret;

  /**
   * Exchanges a service account token for a user-specific access token.
   *
   * <p>This method first obtains a service account token (client credentials grant) and then uses
   * the Keycloak Token Exchange endpoint to impersonate the given user ID. The resulting token
   * response contains an access token that can be used to act on behalf of that user.
   *
   * <p>The operation is protected by a circuit breaker and retry mechanism. On failure, the
   * fallback method {@link #exchangeTokenFallback(String, Throwable)} is invoked.
   *
   * @param userId the internal user ID for which to obtain a token
   * @return a {@link TokenResponse} containing the exchanged access token, refresh token, and
   *     expiration
   * @throws AuthenticationException if the token exchange fails due to invalid subject or user
   *     (HTTP 401 from Keycloak)
   * @throws KeycloakOperationException if Keycloak is unavailable or an unexpected error occurs
   *     (fallback may also rethrow this)
   */
  @CircuitBreaker(name = "keycloakToken", fallbackMethod = "exchangeTokenFallback")
  @Retry(name = "keycloakToken")
  public TokenResponse exchangeTokenForUser(String userId) {
    String serviceToken = getServiceAccountToken();

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("subject_token", serviceToken);
    form.add("requested_subject", userId);

    try {
      Map<String, Object> response = keycloakAuthClient.getToken(realm, form);
      return mapToTokenResponse(response);
    } catch (FeignException.Unauthorized e) {
      log.warn("event=token_exchange_unauthorized userId={}", userId);
      throw new AuthenticationException("Token exchange failed: invalid subject or user");
    } catch (FeignException e) {
      log.error("event=token_exchange_error userId={} status={}", userId, e.status());
      throw new KeycloakOperationException("Keycloak token exchange service unavailable");
    }
  }

  /**
   * Retrieves a service account token (client credentials grant) with caching.
   *
   * <p>This method first checks Redis for a cached token under the key {@code kc:service-token}. If
   * a valid token exists, it is returned. Otherwise, a new token is requested from Keycloak using
   * the client credentials grant. The token is then stored in Redis with an expiration time
   * slightly shorter than its actual validity (10 seconds less) to avoid edge cases where it
   * expires right before use.
   *
   * <p>The token is used as the {@code subject_token} in the token exchange flow to act on behalf
   * of a specific user.
   *
   * @return the service account access token as a string
   * @throws KeycloakOperationException if the token cannot be obtained from Keycloak (e.g., network
   *     failure, invalid credentials)
   */
  private String getServiceAccountToken() {
    String cached = redisTemplate.opsForValue().get("kc:service-token");
    if (cached != null) return cached;

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);

    try {
      Map<String, Object> response = keycloakAuthClient.getToken(realm, form);
      TokenResponse tokenResponse = mapToTokenResponse(response);

      redisTemplate
          .opsForValue()
          .set(
              "kc:service-token",
              tokenResponse.accessToken(),
              Duration.ofSeconds(tokenResponse.expiresIn() - 10));
      return tokenResponse.accessToken();
    } catch (FeignException e) {
      log.error("event=service_token_fetch_error", e);
      throw new KeycloakOperationException("Failed to obtain service account token");
    }
  }

  /**
   * Maps a Keycloak token response map to a {@link TokenResponse} DTO.
   *
   * <p>Extracts {@code access_token}, {@code refresh_token}, and {@code expires_in} from the raw
   * map returned by the Feign client.
   *
   * @param response the raw response map from Keycloak (never {@code null})
   * @return a {@link TokenResponse} built from the map values
   * @throws ClassCastException if the values are not of the expected types
   */
  private TokenResponse mapToTokenResponse(Map<String, Object> response) {
    return TokenResponse.builder()
        .accessToken((String) response.get("access_token"))
        .refreshToken((String) response.get("refresh_token"))
        .expiresIn(((Number) response.get("expires_in")).intValue())
        .build();
  }

  /**
   * Fallback method for {@link #exchangeTokenForUser(String)} when the circuit breaker is open or a
   * retry attempt fails.
   *
   * <p>This method distinguishes between business exceptions (like {@link AuthenticationException})
   * and infrastructure failures. Business exceptions are rethrown as-is to preserve the correct
   * semantic for the caller. All other failures (network timeouts, circuit open, etc.) are wrapped
   * in a generic {@link KeycloakOperationException} with a user-friendly message.
   *
   * @param userId the user ID (original argument)
   * @param t the exception that triggered the fallback
   * @return never returns normally; always throws an exception
   * @throws AuthenticationException if the original failure was an authentication problem
   *     (re-thrown)
   * @throws KeycloakOperationException for all other failures
   */
  // Fallback for exchangeTokenForUser
  private TokenResponse exchangeTokenFallback(String userId, Throwable t) {
    log.error("event=token_exchange_circuit_open userId={}", userId, t);

    // Rethrow business exceptions exactly as they were
    if (t instanceof AuthenticationException || t instanceof IllegalArgumentException) {
      log.warn("event=token_exchange_circuit_rethrow exception={}", t.getClass().getSimpleName());
      throw (RuntimeException) t;
    }

    // For all other failures (network, timeout, etc.) fallback to generic error
    throw new KeycloakOperationException(
        "Authentication service temporarily unavailable. Please try again later.");
  }
}
