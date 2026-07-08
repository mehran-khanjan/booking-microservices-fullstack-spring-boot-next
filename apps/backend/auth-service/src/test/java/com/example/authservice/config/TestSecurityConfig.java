package com.example.authservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Spring Security's OAuth2 resource-server configuration requires a {@link JwtDecoder} bean to
 * exist in the context in order to build the {@code SecurityFilterChain}, even for test classes
 * that never actually present a bearer token (e.g. hitting the public sign-up endpoint). We don't
 * want tests to depend on a real Keycloak instance or the network, so we provide a stub decoder
 * that simply fails any decode attempt - it is never invoked by tests that only exercise permitAll
 * routes or that expect a 401 for missing credentials.
 */
@TestConfiguration
public class TestSecurityConfig {

  @Bean
  @Primary
  public JwtDecoder testJwtDecoder() {
    return token -> {
      throw new JwtException("Decoding is not supported in this test context");
    };
  }
}
