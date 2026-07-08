package com.example.authservice.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

  @Test
  void allowedOrigins_getterAndSetter_roundTrip() {
    CorsProperties properties = new CorsProperties();
    List<String> origins = List.of("http://localhost:3000", "https://app.example.com");

    properties.setAllowedOrigins(origins);

    assertThat(properties.getAllowedOrigins()).containsExactlyElementsOf(origins);
  }

  @Test
  void allowedOrigins_defaultsToNull() {
    CorsProperties properties = new CorsProperties();

    assertThat(properties.getAllowedOrigins()).isNull();
  }
}
