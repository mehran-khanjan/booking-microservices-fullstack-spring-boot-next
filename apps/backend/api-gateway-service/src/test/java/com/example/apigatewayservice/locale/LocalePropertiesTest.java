package com.example.apigatewayservice.locale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class LocalePropertiesTest {

  @Test
  void defaultsComeFromAppLocaleConstants() {
    LocaleProperties properties = new LocaleProperties();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.isRequired()).isTrue();
    assertThat(properties.getSupportedLanguages()).isEmpty();
    assertThat(properties.isSkipDiagnosticPaths()).isTrue();
    // Default locale/locales come from com.example.commonlib.locale.AppLocale
    assertThat(properties.getDefaultLocale()).isNotNull();
    assertThat(properties.getSupportedLocales()).isNotNull();
  }

  @Test
  void gettersAndSettersRoundTrip() {
    LocaleProperties properties = new LocaleProperties();

    properties.setEnabled(false);
    properties.setRequired(false);
    properties.setSupportedLanguages(Set.of("en", "fr"));
    properties.setSupportedLocales(Set.of("en-US", "fr-FR"));
    properties.setDefaultLocale("fr-FR");
    properties.setSkipDiagnosticPaths(false);

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.isRequired()).isFalse();
    assertThat(properties.getSupportedLanguages()).containsExactlyInAnyOrder("en", "fr");
    assertThat(properties.getSupportedLocales()).containsExactlyInAnyOrder("en-US", "fr-FR");
    assertThat(properties.getDefaultLocale()).isEqualTo("fr-FR");
    assertThat(properties.isSkipDiagnosticPaths()).isFalse();
  }

  @Test
  void equalsAndHashCodeWork() {
    LocaleProperties a = new LocaleProperties();
    LocaleProperties b = new LocaleProperties();

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());

    b.setRequired(false);
    assertThat(a).isNotEqualTo(b);
  }
}
