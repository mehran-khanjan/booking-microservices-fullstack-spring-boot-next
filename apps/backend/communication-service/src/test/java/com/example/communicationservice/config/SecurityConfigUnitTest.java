package com.example.communicationservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.communicationservice.property.CorsProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class SecurityConfigUnitTest {

  private CorsProperties corsProperties() {
    CorsProperties properties = new CorsProperties();
    properties.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8080"));
    return properties;
  }

  @Test
  void jwtAuthenticationConverter_extractsRealmRoles_prefixingWhenMissing() {
    SecurityConfig securityConfig = new SecurityConfig(corsProperties());
    JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "user-123")
            .claim("realm_access", Map.of("roles", List.of("ADMIN", "ROLE_USER")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

    JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

    assertThat(token).isNotNull();
    assertThat(token.getName()).isEqualTo("user-123");
    List<String> authorityNames =
        token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    assertThat(authorityNames).contains("ROLE_ADMIN", "ROLE_USER");
  }

  @Test
  void jwtAuthenticationConverter_withoutRealmAccessClaim_returnsNoRealmAuthorities() {
    SecurityConfig securityConfig = new SecurityConfig(corsProperties());
    JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "user-456")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

    JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

    assertThat(token).isNotNull();
    List<String> roleAuthorities =
        token.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .toList();
    assertThat(roleAuthorities).isEmpty();
  }

  @Test
  void jwtAuthenticationConverter_realmAccessWithoutRolesKey_returnsNoRealmAuthorities() {
    SecurityConfig securityConfig = new SecurityConfig(corsProperties());
    JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();

    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "user-789")
            .claim("realm_access", Map.of("otherKey", "value"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

    JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);

    List<String> roleAuthorities =
        token.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .toList();
    assertThat(roleAuthorities).isEmpty();
  }

  @Test
  void corsConfigurationSource_appliesConfiguredOriginsToAllPaths() {
    SecurityConfig securityConfig = new SecurityConfig(corsProperties());

    CorsConfigurationSource source = securityConfig.corsConfigurationSource();
    assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/communication/admin/dlq/email/replay");
    CorsConfiguration config = source.getCorsConfiguration(request);

    assertThat(config).isNotNull();
    assertThat(config.getAllowedOrigins())
        .containsExactly("http://localhost:3000", "http://localhost:8080");
    assertThat(config.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
    assertThat(config.getAllowCredentials()).isTrue();
  }
}
