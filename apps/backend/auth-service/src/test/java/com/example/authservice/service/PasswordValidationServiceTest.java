package com.example.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commonlib.exception.PasswordPolicyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordValidationServiceTest {

  private PasswordValidationService service;

  @BeforeEach
  void setUp() {
    service = new PasswordValidationService();
    ReflectionTestUtils.setField(service, "minLength", 8);
  }

  @Test
  void validatePassword_strongPassword_doesNotThrow() {
    service.validatePassword("Passw0rd!");
  }

  @Test
  void validatePassword_tooShort_throwsPasswordPolicyException() {
    assertThatThrownBy(() -> service.validatePassword("P0w!"))
        .isInstanceOf(PasswordPolicyException.class)
        .hasMessageContaining("8");
  }

  @Test
  void validatePassword_missingUppercase_throws() {
    assertThatThrownBy(() -> service.validatePassword("passw0rd!"))
        .isInstanceOf(PasswordPolicyException.class);
  }

  @Test
  void validatePassword_missingLowercase_throws() {
    assertThatThrownBy(() -> service.validatePassword("PASSW0RD!"))
        .isInstanceOf(PasswordPolicyException.class);
  }

  @Test
  void validatePassword_missingDigit_throws() {
    assertThatThrownBy(() -> service.validatePassword("Password!"))
        .isInstanceOf(PasswordPolicyException.class);
  }

  @Test
  void validatePassword_missingSpecialChar_throws() {
    assertThatThrownBy(() -> service.validatePassword("Passw0rd1"))
        .isInstanceOf(PasswordPolicyException.class);
  }

  @Test
  void validatePassword_containingWhitespace_throws() {
    assertThatThrownBy(() -> service.validatePassword("Pass w0rd!"))
        .isInstanceOf(PasswordPolicyException.class);
  }

  @Test
  void checkPasswordHistory_alwaysReturnsTrue_untilImplemented() {
    assertThat(service.checkPasswordHistory("user-id", "AnyPassw0rd!")).isTrue();
  }
}
