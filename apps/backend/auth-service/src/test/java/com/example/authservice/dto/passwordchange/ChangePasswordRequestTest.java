package com.example.authservice.dto.passwordchange;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChangePasswordRequestTest {

  @Test
  void accessors_returnConstructorValues() {
    ChangePasswordRequest dto = new ChangePasswordRequest("oldPass1!", "newPass1!");

    assertThat(dto.oldPassword()).isEqualTo("oldPass1!");
    assertThat(dto.newPassword()).isEqualTo("newPass1!");
  }

  @Test
  void equalsAndHashCode_areValueBased() {
    ChangePasswordRequest a = new ChangePasswordRequest("old", "new");
    ChangePasswordRequest b = new ChangePasswordRequest("old", "new");

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}
