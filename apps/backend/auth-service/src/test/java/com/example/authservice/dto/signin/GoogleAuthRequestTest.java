package com.example.authservice.dto.signin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoogleAuthRequestTest {

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
  void validIdToken_hasNoViolations() {
    GoogleAuthRequest dto = new GoogleAuthRequest("a-valid-looking-jwt");

    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  void blankIdToken_producesViolation() {
    GoogleAuthRequest dto = new GoogleAuthRequest("");

    Set<ConstraintViolation<GoogleAuthRequest>> violations = validator.validate(dto);

    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("idToken"));
  }

  @Test
  void nullIdToken_producesViolation() {
    GoogleAuthRequest dto = new GoogleAuthRequest(null);

    assertThat(validator.validate(dto)).isNotEmpty();
  }
}
