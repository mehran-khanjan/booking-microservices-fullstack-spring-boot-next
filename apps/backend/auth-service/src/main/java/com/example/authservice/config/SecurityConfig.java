package com.example.authservice.config;

import com.example.authservice.property.CorsProperties;
import com.example.commonlib.route.ApiRoutes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration for the Auth service.
 *
 * <p>Sets up stateless JWT‑based authentication with Keycloak as the OAuth2 resource server,
 * extracts realm roles, and configures CORS.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final CorsProperties corsProperties;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())

        // 1. Stateless (no sessions)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 3. CORS (adjust allowed origins as needed)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        // 4. Authorize requests
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public endpoints (no token required)
                    .requestMatchers(ApiRoutes.Auth.SIGN_UP, ApiRoutes.Auth.SIGN_UP_TS)
                    .permitAll()
                    // Authenticated endpoints (valid JWT required)

                    // for /error route
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated() // all other endpoints secured
            )

        // 5. OAuth2 Resource Server with custom JWT converter
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

    return http.build();
  }

  /**
   * Custom JWT converter to extract: - Principal name = "sub" (user ID) – used
   * in @AuthenticationPrincipal - Authorities from realm_access.roles
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

    // Set principal claim to "sub" (Keycloak user ID)
    converter.setPrincipalClaimName("sub");

    // Convert roles from JWT
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          // Use default converter for standard scopes
          Collection<GrantedAuthority> defaultAuthorities =
              new JwtGrantedAuthoritiesConverter().convert(jwt);

          // Extract realm roles
          Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt);

          // Combine
          return Stream.concat(defaultAuthorities.stream(), realmRoles.stream())
              .collect(Collectors.toSet());
        });

    return converter;
  }

  /** Extracts roles from Keycloak's "realm_access.roles" */
  private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess == null || !realmAccess.containsKey("roles")) {
      return List.of();
    }

    @SuppressWarnings("unchecked")
    List<String> roles = (List<String>) realmAccess.get("roles");

    return roles.stream()
        // Ensure roles are prefixed with "ROLE_" (Spring Security default)
        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
  }

  /** CORS configuration – adjust allowed origins for your frontend */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    config.setAllowedOrigins(corsProperties.getAllowedOrigins());

    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

    config.setAllowedHeaders(List.of("*"));

    config.setAllowCredentials(true); // allow cookies to be sent

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

    source.registerCorsConfiguration("/**", config);

    return source;
  }
}
