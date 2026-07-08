package com.example.apigatewayservice.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyPropertiesTest {

  @Test
  void defaultsAreSensible() {
    IdempotencyProperties properties = new IdempotencyProperties();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getMaxLength()).isEqualTo(256);
    assertThat(properties.isSkipDiagnosticPaths()).isTrue();
  }

  @Test
  void gettersAndSettersRoundTrip() {
    IdempotencyProperties properties = new IdempotencyProperties();

    properties.setEnabled(false);
    properties.setMaxLength(128);
    properties.setSkipDiagnosticPaths(false);

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getMaxLength()).isEqualTo(128);
    assertThat(properties.isSkipDiagnosticPaths()).isFalse();
  }

  @Test
  void equalsAndHashCodeAndToStringWork() {
    IdempotencyProperties a = new IdempotencyProperties();
    IdempotencyProperties b = new IdempotencyProperties();

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).contains("enabled");

    b.setEnabled(false);
    assertThat(a).isNotEqualTo(b);
  }
}
