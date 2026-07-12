package com.example.authservice.dto.otp;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PhoneOtpRequestTest {

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
  void validE164PhoneNumber_hasNoViolations() {
    PhoneOtpRequest dto = new PhoneOtpRequest("+15551234567", "Passw0rd!");

    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  void blankPhoneNumber_producesViolation() {
    PhoneOtpRequest dto = new PhoneOtpRequest("", "Passw0rd!");

    assertThat(validator.validate(dto)).isNotEmpty();
  }

  @Test
  void phoneNumberWithoutLeadingPlus_producesViolation() {
    PhoneOtpRequest dto = new PhoneOtpRequest("15551234567", "Passw0rd!");

    Set<ConstraintViolation<PhoneOtpRequest>> violations = validator.validate(dto);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber"));
  }

  @Test
  void phoneNumberWithNonDigits_producesViolation() {
    PhoneOtpRequest dto = new PhoneOtpRequest("+1555ABC4567", "Passw0rd!");

    assertThat(validator.validate(dto)).isNotEmpty();
  }
}
