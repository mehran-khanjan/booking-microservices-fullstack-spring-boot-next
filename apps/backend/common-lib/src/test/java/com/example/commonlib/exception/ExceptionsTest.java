package com.example.commonlib.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExceptionsTest {

  @Test
  void keycloakOperationException_hasMessage() {
    KeycloakOperationException ex = new KeycloakOperationException("test");
    assertThat(ex).hasMessage("test");
  }

  @Test
  void userAlreadyExistsException_hasMessage() {
    UserAlreadyExistsException ex = new UserAlreadyExistsException("exists");
    assertThat(ex).hasMessage("exists");
  }
}
