package com.example.authservice.dto.signup;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VerifyUserEmailRequestDtoTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeFactory() {
    factory.close();
  }

  @Test
  void validRequest_hasNoViolations() {
    VerifyUserEmailRequestDto dto = new VerifyUserEmailRequestDto("user@example.com", "123456");

    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  void blankEmail_producesViolation() {
    VerifyUserEmailRequestDto dto = new VerifyUserEmailRequestDto("", "123456");

    assertThat(validator.validate(dto)).isNotEmpty();
  }

  @Test
  void blankOtp_producesViolation() {
    VerifyUserEmailRequestDto dto = new VerifyUserEmailRequestDto("user@example.com", "");

    assertThat(validator.validate(dto)).isNotEmpty();
  }
}
