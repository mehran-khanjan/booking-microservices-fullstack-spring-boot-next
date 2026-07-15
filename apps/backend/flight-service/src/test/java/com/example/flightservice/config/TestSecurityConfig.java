package com.example.flightservice.config;

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

@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

  public static final String VALID_TOKEN = "test-valid-token";

  @Bean
  @Primary
  public JwtDecoder testJwtDecoder() {
    return token -> {
      Instant now = Instant.now();
      if (VALID_TOKEN.equals(token)) {
        return Jwt.withTokenValue(token)
            .header("alg", "none")
            .claim("sub", "test-user")
            .claim("realm_access", Map.of("roles", List.of("USER")))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
      }
      return Jwt.withTokenValue(token)
          .header("alg", "none")
          .claim("sub", "unknown")
          .claim("realm_access", Map.of("roles", List.of()))
          .issuedAt(now)
          .expiresAt(now.plusSeconds(300))
          .build();
    };
  }

  @Bean
  public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(ApiRoutes.Flight.FLIGHT_SEARCH, ApiRoutes.Flight.FLIGHT_SEARCH_TS)
            .hasAuthority(ROLES.USER.getKeycloakRoleName())
            .requestMatchers("/error").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(testJwtAuthenticationConverter())));
    return http.build();
  }

  private JwtAuthenticationConverter testJwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName("sub");
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
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
    if (realmAccess == null || !realmAccess.containsKey("roles")) return List.of();
    @SuppressWarnings("unchecked")
    List<String> roles = (List<String>) realmAccess.get("roles");
    return roles.stream()
        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
  }

  @Bean
  public Role role() {
    return new Role();
  }

  public static class Role {
    public String user() { return ROLES.USER.getKeycloakRoleName(); }
    public String admin() { return ROLES.ADMIN.getKeycloakRoleName(); }
  }
}