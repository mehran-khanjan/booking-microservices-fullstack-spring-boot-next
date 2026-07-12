package com.example.authservice.dto.otp;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PhoneOtpVerifyRequestTest {

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
    PhoneOtpVerifyRequest dto = new PhoneOtpVerifyRequest("+15551234567", "123456");

    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  void blankPhoneNumber_producesViolation() {
    PhoneOtpVerifyRequest dto = new PhoneOtpVerifyRequest("", "123456");

    assertThat(validator.validate(dto)).isNotEmpty();
  }

  @Test
  void blankOtp_producesViolation() {
    PhoneOtpVerifyRequest dto = new PhoneOtpVerifyRequest("+15551234567", "");

    assertThat(validator.validate(dto)).isNotEmpty();
  }
}
