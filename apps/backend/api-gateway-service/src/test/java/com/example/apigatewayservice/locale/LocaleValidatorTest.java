package com.example.apigatewayservice.locale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LocaleValidatorTest {

  private LocaleProperties properties;
  private LocaleValidator validator;

  @BeforeEach
  void setUp() {
    properties = new LocaleProperties();
    properties.setSupportedLanguages(Set.of());
    properties.setSupportedLocales(Set.of());
    validator = new LocaleValidator(properties);
  }

  @Nested
  @DisplayName("isValid")
  class IsValid {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsNullOrBlank(String header) {
      assertThat(validator.isValid(header)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "en-US", "fr-FR", "en-US,en;q=0.9,fr;q=0.8", "zh-CN"})
    void acceptsWellFormedHeaders(String header) {
      assertThat(validator.isValid(header)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"english", "EN_US", "en--US", "123", "en-us-extra-part"})
    void rejectsMalformedHeaders(String header) {
      assertThat(validator.isValid(header)).isFalse();
    }

    @Test
    @DisplayName("enforces supported languages list when configured")
    void enforcesSupportedLanguages() {
      properties.setSupportedLanguages(Set.of("en", "fr"));

      assertThat(validator.isValid("en-US")).isTrue();
      assertThat(validator.isValid("de-DE")).isFalse();
    }

    @Test
    @DisplayName("enforces supported locales list when configured")
    void enforcesSupportedLocales() {
      properties.setSupportedLocales(Set.of("en-US"));

      assertThat(validator.isValid("en-US")).isTrue();
      assertThat(validator.isValid("en-GB")).isFalse();
    }

    @Test
    @DisplayName("matches on language code fallback when only language configured")
    void matchesLanguageCodeFallback() {
      properties.setSupportedLanguages(Set.of("en"));

      assertThat(validator.isValid("en-GB")).isTrue();
    }
  }

  @Nested
  @DisplayName("extractPrimaryLanguage")
  class ExtractPrimaryLanguage {

    @Test
    void extractsFromSimpleHeader() {
      assertThat(validator.extractPrimaryLanguage("en-US")).isEqualTo("en-US");
    }

    @Test
    void extractsFirstFromCommaSeparatedList() {
      assertThat(validator.extractPrimaryLanguage("en-US,en;q=0.9,fr;q=0.8")).isEqualTo("en-US");
    }

    @Test
    void stripsQualityValue() {
      assertThat(validator.extractPrimaryLanguage("fr;q=0.8")).isEqualTo("fr");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void returnsNullForNullOrBlank(String header) {
      assertThat(validator.extractPrimaryLanguage(header)).isNull();
    }
  }

  @Nested
  @DisplayName("isValidLanguageTag")
  class IsValidLanguageTag {

    @ParameterizedTest
    @ValueSource(strings = {"en", "en-US", "fr-FR"})
    void acceptsValidTags(String tag) {
      assertThat(validator.isValidLanguageTag(tag)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"english", "EN", "en-us"})
    void rejectsInvalidTags(String tag) {
      assertThat(validator.isValidLanguageTag(tag)).isFalse();
    }
  }

  @Nested
  @DisplayName("parseLocale")
  class ParseLocale {

    @Test
    void parsesLanguageAndCountry() {
      Locale locale = validator.parseLocale("en-US");
      assertThat(locale.getLanguage()).isEqualTo("en");
      assertThat(locale.getCountry()).isEqualTo("US");
    }

    @Test
    void parsesLanguageOnly() {
      Locale locale = validator.parseLocale("en");
      assertThat(locale.getLanguage()).isEqualTo("en");
    }

    @Test
    void returnsNullForInvalidTag() {
      assertThat(validator.parseLocale("not-valid-tag")).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void returnsNullForNullOrBlank(String header) {
      assertThat(validator.parseLocale(header)).isNull();
    }
  }

  @Test
  void getDefaultLocaleDelegatesToProperties() {
    properties.setDefaultLocale("fr-FR");
    assertThat(validator.getDefaultLocale()).isEqualTo("fr-FR");
  }

  @Test
  void isEnabledDelegatesToProperties() {
    properties.setEnabled(false);
    assertThat(validator.isEnabled()).isFalse();
  }

  @Test
  void isRequiredDelegatesToProperties() {
    properties.setRequired(false);
    assertThat(validator.isRequired()).isFalse();
  }
}
