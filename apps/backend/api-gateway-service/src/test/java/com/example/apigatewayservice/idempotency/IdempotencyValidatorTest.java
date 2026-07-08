package com.example.apigatewayservice.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyValidatorTest {

  private IdempotencyProperties properties;
  private IdempotencyValidator validator;

  @BeforeEach
  void setUp() {
    properties = new IdempotencyProperties();
    validator = new IdempotencyValidator(properties);
  }

  @Nested
  @DisplayName("requiresIdempotencyCheck")
  class RequiresIdempotencyCheck {

    @ParameterizedTest
    @ValueSource(
        strings = {"POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT"})
    @DisplayName("returns true for all mutating/verb methods")
    void returnsTrueForCheckableMethods(String method) {
      assertThat(validator.requiresIdempotencyCheck(method)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "Put", "pAtCh"})
    @DisplayName("is case-insensitive")
    void isCaseInsensitive(String method) {
      assertThat(validator.requiresIdempotencyCheck(method)).isTrue();
    }

    @Test
    @DisplayName("returns false for GET")
    void returnsFalseForGet() {
      assertThat(validator.requiresIdempotencyCheck("GET")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("returns false for null/blank input")
    void returnsFalseForNullOrBlank(String method) {
      assertThat(validator.requiresIdempotencyCheck(method)).isFalse();
    }

    @Test
    @DisplayName("returns false for unknown method")
    void returnsFalseForUnknownMethod() {
      assertThat(validator.requiresIdempotencyCheck("FOOBAR")).isFalse();
    }
  }

  @Nested
  @DisplayName("isValidUUID")
  class IsValidUuid {

    @Test
    @DisplayName("accepts a well-formed UUID")
    void acceptsWellFormedUuid() {
      assertThat(validator.isValidUUID("550e8400-e29b-41d4-a716-446655440000")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("rejects null/blank")
    void rejectsNullOrBlank(String key) {
      assertThat(validator.isValidUUID(key)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
      "not-a-uuid",
      "550e8400-e29b-41d4-a716",
      "550e8400e29b41d4a716446655440000",
      "550e8400-e29b-41d4-a716-446655440000-extra"
    })
    @DisplayName("rejects malformed values")
    void rejectsMalformedValues(String key) {
      assertThat(validator.isValidUUID(key)).isFalse();
    }

    @Test
    @DisplayName("rejects value exceeding max length before regex check")
    void rejectsTooLong() {
      properties.setMaxLength(10);
      assertThat(validator.isValidUUID("550e8400-e29b-41d4-a716-446655440000")).isFalse();
    }

    @Test
    @DisplayName("accepts uppercase hex UUID")
    void acceptsUppercaseUuid() {
      assertThat(validator.isValidUUID("550E8400-E29B-41D4-A716-446655440000")).isTrue();
    }
  }

  @Test
  @DisplayName("getCheckableMethods returns an immutable copy containing expected methods")
  void getCheckableMethodsReturnsExpectedSet() {
    Set<String> methods = validator.getCheckableMethods();
    assertThat(methods)
        .containsExactlyInAnyOrder(
            "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT");
    assertThat(methods).doesNotContain("GET");
  }
}
