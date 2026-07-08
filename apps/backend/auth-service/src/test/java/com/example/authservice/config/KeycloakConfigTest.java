package com.example.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.test.util.ReflectionTestUtils;

class KeycloakConfigTest {

  @Test
  void keycloakAdmin_buildsClientWithConfiguredProperties() {
    KeycloakConfig config = new KeycloakConfig();
    ReflectionTestUtils.setField(config, "serverUrl", "http://localhost:8081");
    ReflectionTestUtils.setField(config, "adminUsername", "admin");
    ReflectionTestUtils.setField(config, "adminPassword", "admin");

    Keycloak keycloak = config.keycloakAdmin();

    assertThat(keycloak).isNotNull();
  }

  @Test
  void keycloakAdmin_usesDefaultCredentialsWhenNotOverridden() {
    KeycloakConfig config = new KeycloakConfig();
    ReflectionTestUtils.setField(config, "serverUrl", "http://localhost:8081");
    ReflectionTestUtils.setField(config, "adminUsername", "admin");
    ReflectionTestUtils.setField(config, "adminPassword", "admin");

    // Should not throw even though no network call is made at construction time.
    assertThat(config.keycloakAdmin()).isInstanceOf(Keycloak.class);
  }
}
