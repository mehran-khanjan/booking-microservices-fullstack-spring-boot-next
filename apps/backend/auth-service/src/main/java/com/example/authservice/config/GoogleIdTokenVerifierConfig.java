package com.example.authservice.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for creating a {@link GoogleIdTokenVerifier} bean.
 *
 * <p>This verifier is used to validate Google ID tokens (OpenID Connect) during authentication
 * flows. It is configured with the OAuth 2.0 client ID obtained from application properties, which
 * is required to verify that the token's audience matches the expected client ID.
 *
 * <p>The verifier uses the Google API Client Library's default {@link NetHttpTransport} and {@link
 * GsonFactory} for HTTP communication and JSON parsing, respectively.
 *
 * <p>This bean is intended to be injected into services that handle Google authentication, such as
 * {@code GoogleOAuth2Service} or custom authentication filters.
 *
 * @author Your Team
 * @see GoogleIdTokenVerifier
 * @since 1.0
 */
@Configuration
public class GoogleIdTokenVerifierConfig {

  /**
   * Creates a {@link GoogleIdTokenVerifier} bean pre-configured with the client ID from the
   * application's properties.
   *
   * <p>The verifier is built using:
   *
   * <ul>
   *   <li>{@link NetHttpTransport} – a lightweight HTTP transport based on {@code
   *       java.net.HttpURLConnection}
   *   <li>{@link GsonFactory} – a JSON factory using Gson for parsing
   *   <li>A singleton list containing the provided client ID as the allowed audience
   * </ul>
   *
   * <p>When verifying a token, the verifier will check that the token's {@code aud} (audience)
   * claim matches this client ID. If the client ID is not set or invalid, token verification will
   * fail, preventing unauthorized access.
   *
   * @param clientId the OAuth 2.0 client ID for this application, injected from the property {@code
   *     google.oauth.client-id} (must not be {@code null} or empty)
   * @return a fully constructed {@link GoogleIdTokenVerifier} instance
   */
  @Bean
  public GoogleIdTokenVerifier googleIdTokenVerifier(
      @Value("${google.oauth.client-id}") String clientId) {
    return new GoogleIdTokenVerifier.Builder(
            new NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(Collections.singletonList(clientId))
        .build();
  }
}
