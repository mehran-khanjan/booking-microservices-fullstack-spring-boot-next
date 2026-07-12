package com.example.flightservice.config;

import com.example.commonlib.enums.ROLES;
import com.example.commonlib.route.ApiRoutes;
import com.example.flightservice.property.CorsProperties;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration for the Flight Service.
 *
 * <p>This configuration sets up:
 *
 * <ul>
 *   <li>Stateless (no session) security
 *   <li>OAuth2 resource server with JWT decoding
 *   <li>Custom JWT authentication converter that extracts both standard scopes and Keycloak realm
 *       roles
 *   <li>Role‑based access control (authorisation)
 *   <li>CORS configuration
 * </ul>
 *
 * The principal name is set to the JWT {@code sub} claim (user ID).
 *
 * @see ROLES
 * @see ApiRoutes
 * @see CorsProperties
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /**
   * The issuer URI for the OAuth2 provider (e.g., Keycloak). Used to fetch the JWKS and validate
   * JWT tokens.
   */
  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  private String issuerUri;

  /** CORS properties (allowed origins) injected from application configuration. */
  private final CorsProperties corsProperties;

  /**
   * Main security filter chain.
   *
   * <p>Defines which endpoints are public, which require specific roles, and configures JWT
   * validation.
   *
   * @param http the {@link HttpSecurity} to configure
   * @return the built {@link SecurityFilterChain}
   * @throws Exception if configuration fails
   */
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
                    .requestMatchers(
                        ApiRoutes.Flight.FLIGHT_SEARCH, ApiRoutes.Flight.FLIGHT_SEARCH_TS)
                    .hasAuthority(ROLES.USER.getKeycloakRoleName())

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
   * Custom JWT converter that:
   *
   * <ul>
   *   <li>Sets the principal name to the {@code sub} claim (user ID)
   *   <li>Extracts {@code realm_access.roles} from Keycloak and prefixes them with {@code ROLE_}
   *       (Spring Security default)
   *   <li>Combines default authorities (scopes) with realm roles
   * </ul>
   *
   * @return a configured {@link JwtAuthenticationConverter}
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

  /**
   * Extracts roles from Keycloak's {@code realm_access.roles} claim. Each role is prefixed with
   * {@code ROLE_} to match Spring Security's default authority prefix.
   *
   * @param jwt the JWT token
   * @return a collection of granted authorities (roles)
   */
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

  /**
   * CORS configuration – allows cross‑origin requests from the origins defined in {@link
   * CorsProperties#getAllowedOrigins()}.
   *
   * @return a {@link CorsConfigurationSource} with configured CORS settings
   */
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

  /**
   * JWT decoder that uses the configured issuer URI to fetch the JWKS.
   *
   * @return a {@link JwtDecoder} that validates tokens from the OIDC issuer
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    return JwtDecoders.fromIssuerLocation(issuerUri);
  }
}
