package com.example.authservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EmailReadUserRequestDtoTest {

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
  void validDto_hasNoViolations() {
    EmailReadUserRequestDto dto = new EmailReadUserRequestDto("user@example.com", "secretPass1");

    Set<ConstraintViolation<EmailReadUserRequestDto>> violations = validator.validate(dto);

    assertThat(violations).isEmpty();
  }

  @Test
  void blankEmail_producesViolation() {
    EmailReadUserRequestDto dto = new EmailReadUserRequestDto("", "secretPass1");

    Set<ConstraintViolation<EmailReadUserRequestDto>> violations = validator.validate(dto);

    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
  }

  @Test
  void malformedEmail_producesViolation() {
    EmailReadUserRequestDto dto = new EmailReadUserRequestDto("not-an-email", "secretPass1");

    Set<ConstraintViolation<EmailReadUserRequestDto>> violations = validator.validate(dto);

    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
  }

  @Test
  void blankPassword_producesViolation() {
    EmailReadUserRequestDto dto = new EmailReadUserRequestDto("user@example.com", "");

    Set<ConstraintViolation<EmailReadUserRequestDto>> violations = validator.validate(dto);

    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
  }

  @Test
  void nullEmailAndPassword_producesTwoViolations() {
    EmailReadUserRequestDto dto = new EmailReadUserRequestDto(null, null);

    Set<ConstraintViolation<EmailReadUserRequestDto>> violations = validator.validate(dto);

    assertThat(violations).hasSize(2);
  }

  @Test
  void recordAccessors_returnConstructorValues() {
    EmailReadUserRequestDto dto = new EmailReadUserRequestDto("a@b.com", "pw");

    assertThat(dto.email()).isEqualTo("a@b.com");
    assertThat(dto.password()).isEqualTo("pw");
  }
}
