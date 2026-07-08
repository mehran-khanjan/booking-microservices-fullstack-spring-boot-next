package com.example.commonlib.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiRoutesTest {

  @Test
  void routesAreCorrect() {
    assertThat(ApiRoutes.BASE).isEqualTo("/api/v1");
    assertThat(ApiRoutes.Auth.BASE).isEqualTo("/api/v1/auth");
    assertThat(ApiRoutes.Auth.SIGN_UP).isEqualTo("/api/v1/auth/sign-up/email");
    assertThat(ApiRoutes.Auth.SIGN_UP_TS).isEqualTo("/api/v1/auth/sign-up/email/");
  }
}
