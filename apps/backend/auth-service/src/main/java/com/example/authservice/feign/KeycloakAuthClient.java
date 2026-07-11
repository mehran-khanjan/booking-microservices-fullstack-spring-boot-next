package com.example.authservice.feign;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for interacting with the Keycloak authentication server.
 *
 * <p>This client is responsible for calling Keycloak's OpenID Connect endpoints to obtain tokens
 * (via the token endpoint) and to revoke sessions (via the logout endpoint). It is configured to
 * target the Keycloak server URL defined in the application properties under {@code
 * keycloak.server-url}.
 *
 * <p>The client uses form URL-encoded requests, matching Keycloak's expected content type for token
 * and logout operations.
 *
 * @author Your Team
 * @since 1.0
 */
@FeignClient(name = "keycloak-auth", url = "${keycloak.server-url}")
public interface KeycloakAuthClient {

  /**
   * Exchanges a set of credentials or authorization grants for an access token and refresh token
   * via Keycloak's OIDC token endpoint.
   *
   * <p>This method implements the OAuth 2.0 / OpenID Connect token request as defined in the
   * specification. The {@code formData} parameter should contain the necessary parameters for the
   * grant type being used (e.g., {@code grant_type=password} with {@code username} and {@code
   * password}, or {@code grant_type=refresh_token} with a valid refresh token).
   *
   * <p>The method returns a map containing the token response fields such as {@code access_token},
   * {@code refresh_token}, {@code expires_in}, etc.
   *
   * <p><strong>Example form data for password grant:</strong>
   *
   * <pre>
   * MultiValueMap&lt;String, String&gt; form = new LinkedMultiValueMap<>();
   * form.add("grant_type", "password");
   * form.add("client_id", "my-client");
   * form.add("client_secret", "secret");
   * form.add("username", "user@example.com");
   * form.add("password", "password");
   * </pre>
   *
   * @param realm the Keycloak realm name (e.g., "master" or a custom realm)
   * @param formData the URL-encoded form parameters for the token request
   * @return a map containing the token response (access token, refresh token, expiration, etc.)
   *     from Keycloak
   */
  @PostMapping(
      value = "/realms/{realm}/protocol/openid-connect/token",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  Map<String, Object> getToken(
      @PathVariable("realm") String realm, @RequestBody MultiValueMap<String, String> formData);

  /**
   * Logs out a user session by calling Keycloak's OIDC logout endpoint.
   *
   * <p>This method invalidates the refresh token provided in the form data, effectively revoking
   * the user's session. The request must include the refresh token (or the access token) and client
   * credentials to authenticate the logout request.
   *
   * <p><strong>Example form data for logout:</strong>
   *
   * <pre>
   * MultiValueMap&lt;String, String&gt; form = new LinkedMultiValueMap<>();
   * form.add("client_id", "my-client");
   * form.add("client_secret", "secret");
   * form.add("refresh_token", "refresh-token-value");
   * </pre>
   *
   * <p>If the logout is successful, Keycloak returns a 204 No Content response. Any error (e.g.,
   * invalid token) will be propagated as a Feign exception.
   *
   * @param realm the Keycloak realm name
   * @param formData the URL-encoded form parameters for the logout request
   */
  @PostMapping(
      value = "/realms/{realm}/protocol/openid-connect/logout",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  void logout(
      @PathVariable("realm") String realm, @RequestBody MultiValueMap<String, String> formData);
}
