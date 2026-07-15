package com.example.communicationservice.config;

import com.example.commonlib.enums.ROLES;
import com.example.commonlib.route.ApiRoutes;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Test security configuration that provides a JwtDecoder and SecurityFilterChain for tests.
 * Replaces the real SecurityConfig in tests to avoid connecting to the OIDC provider.
 * Uses the same authorization rules as the real SecurityConfig with a hardcoded JWT decoder.
 */
@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

  public static final String ADMIN_TOKEN = "test-admin-token";
  public static final String USER_TOKEN = "test-user-token";

  @Bean
  @Primary
  public JwtDecoder testJwtDecoder() {
    return token -> {
      Instant now = Instant.now();
      if (ADMIN_TOKEN.equals(token)) {
        return buildJwt(token, "admin-user-id", "admin@example.com", List.of("ADMIN"), now);
      }
      if (USER_TOKEN.equals(token)) {
        return buildJwt(token, "regular-user-id", "user@example.com", List.of("USER"), now);
      }
      // Unknown tokens get a token with no roles - results in 403 for admin endpoints
      return buildJwt(token, "unknown-user", "unknown@example.com", List.of(), now);
    };
  }

  private Jwt buildJwt(String tokenValue, String sub, String email, List<String> roles, Instant now) {
    return Jwt.withTokenValue(tokenValue)
        .header("alg", "none")
        .claim("sub", sub)
        .claim("email", email)
        .claim("realm_access", Map.of("roles", roles))
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .build();
  }

  @Bean
  public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(testCorsConfigurationSource()))
        .authorizeHttpRequests(
            auth ->
                auth
                    .requestMatchers(
                        ApiRoutes.Communication.ADMIN_DQL_EMAIL,
                        ApiRoutes.Communication.ADMIN_DQL_EMAIL_TS,
                        ApiRoutes.Communication.ADMIN_DQL_SMS,
                        ApiRoutes.Communication.ADMIN_DQL_SMS_TS)
                    .hasAuthority(ROLES.ADMIN.getKeycloakRoleName())
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(testJwtAuthenticationConverter())));
    return http.build();
  }

  private JwtAuthenticationConverter testJwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName("sub");
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Collection<GrantedAuthority> defaultAuthorities =
              new JwtGrantedAuthoritiesConverter().convert(jwt);
          Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt);
          return Stream.concat(defaultAuthorities.stream(), realmRoles.stream())
              .collect(Collectors.toSet());
        });
    return converter;
  }

  private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess == null || !realmAccess.containsKey("roles")) {
      return List.of();
    }
    @SuppressWarnings("unchecked")
    List<String> roles = (List<String>) realmAccess.get("roles");
    return roles.stream()
        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
  }

  private CorsConfigurationSource testCorsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8080"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}