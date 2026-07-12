package com.example.authservice.dto.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.authservice.enums.OtpChannel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OtpRequestTest {

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
    OtpRequest dto = new OtpRequest(OtpChannel.EMAIL, "user@example.com", "user-id");

    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  void nullChannel_producesViolation() {
    OtpRequest dto = new OtpRequest(null, "user@example.com", "user-id");

    Set<ConstraintViolation<OtpRequest>> violations = validator.validate(dto);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("channel"));
  }

  @Test
  void blankIdentifier_producesViolation() {
    OtpRequest dto = new OtpRequest(OtpChannel.PHONE, "", "user-id");

    Set<ConstraintViolation<OtpRequest>> violations = validator.validate(dto);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("identifier"));
  }

  @Test
  void blankUserId_producesViolation() {
    OtpRequest dto = new OtpRequest(OtpChannel.PHONE, "+15551234567", "");

    Set<ConstraintViolation<OtpRequest>> violations = validator.validate(dto);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("userId"));
  }
}
