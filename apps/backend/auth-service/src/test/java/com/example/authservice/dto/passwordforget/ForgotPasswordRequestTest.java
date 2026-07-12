package com.example.authservice.dto.passwordforget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ForgotPasswordRequestTest {

  @Test
  void accessor_returnsConstructorValue() {
    ForgotPasswordRequest dto = new ForgotPasswordRequest("user@example.com");

    assertThat(dto.email()).isEqualTo("user@example.com");
  }

  @Test
  void supportsNullEmail_sinceNoValidationConstraintIsDeclared() {
    ForgotPasswordRequest dto = new ForgotPasswordRequest(null);

    assertThat(dto.email()).isNull();
  }
}
