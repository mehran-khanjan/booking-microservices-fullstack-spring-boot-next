package com.example.communicationservice.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleBeanTest {

  @Test
  void admin_returnsAdminRoleName() {
    RoleBean roleBean = new RoleBean();

    assertThat(roleBean.admin()).isEqualTo("ADMIN");
  }

  @Test
  void user_returnsUserRoleName() {
    RoleBean roleBean = new RoleBean();

    assertThat(roleBean.user()).isEqualTo("USER");
  }
}
