package com.example.authservice.dto.signin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PhoneLoginRequestTest {

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
    PhoneLoginRequest dto = new PhoneLoginRequest("+15551234567", "Passw0rd!");

    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  void blankPhoneNumber_producesViolation() {
    PhoneLoginRequest dto = new PhoneLoginRequest("", "Passw0rd!");

    assertThat(validator.validate(dto)).isNotEmpty();
  }

  @Test
  void blankPassword_producesViolation() {
    PhoneLoginRequest dto = new PhoneLoginRequest("+15551234567", "");

    assertThat(validator.validate(dto)).isNotEmpty();
  }
}
