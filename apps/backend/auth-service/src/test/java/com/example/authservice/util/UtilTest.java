package com.example.authservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UtilTest {

  @Test
  void mask_null_returnsStars() {
    assertThat(Util.mask(null)).isEqualTo("***");
  }

  @Test
  void mask_email_masksLocalPartKeepingDomain() {
    assertThat(Util.mask("johndoe@example.com")).isEqualTo("jo***@example.com");
  }

  @Test
  void mask_email_withSingleCharacterLocalPart() {
    assertThat(Util.mask("j@example.com")).isEqualTo("j***@example.com");
  }

  @Test
  void mask_email_withTwoCharacterLocalPart() {
    assertThat(Util.mask("jo@example.com")).isEqualTo("jo***@example.com");
  }

  @Test
  void mask_nonEmail_shortValue_returnsAllStars() {
    assertThat(Util.mask("123")).isEqualTo("****");
  }

  @Test
  void mask_nonEmail_exactlyFourCharacters_returnsAllStars() {
    // length < 4 is false for length == 4, so last 4 chars are kept
    assertThat(Util.mask("1234")).isEqualTo("****1234");
  }

  @Test
  void mask_nonEmail_longValue_keepsLastFourDigits() {
    assertThat(Util.mask("+15551234567")).isEqualTo("****4567");
  }

  @Test
  void mask_emptyString_returnsAllStars() {
    assertThat(Util.mask("")).isEqualTo("****");
  }
}
