package com.example.commonlib.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ROLESTest {

  @Test
  void valuesAndKeycloakNames() {
    assertThat(ROLES.USER.getKeycloakRoleName()).isEqualTo("ROLE_USER");
    assertThat(ROLES.ADMIN.getKeycloakRoleName()).isEqualTo("ROLE_ADMIN");
    assertThat(ROLES.values()).containsExactly(ROLES.USER, ROLES.ADMIN);
  }
}
