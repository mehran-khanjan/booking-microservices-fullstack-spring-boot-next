package com.example.authservice.config;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for the Keycloak admin client, used to manage users and realms. */
@Configuration
@Slf4j
public class KeycloakConfig {

  @Value("${keycloak.admin.server-url}")
  private String serverUrl;

  @Value("${keycloak.admin.username:admin}")
  private String adminUsername;

  @Value("${keycloak.admin.password:admin}")
  private String adminPassword;

  @Bean
  public Keycloak keycloakAdmin() {
    log.info("Connecting to Keycloak admin at: {}", serverUrl);

    return KeycloakBuilder.builder()
        .serverUrl(serverUrl)
        .realm("master")
        .clientId("admin-cli")
        .username(adminUsername)
        .password(adminPassword)
        .grantType(OAuth2Constants.PASSWORD)
        .build();
  }
}
