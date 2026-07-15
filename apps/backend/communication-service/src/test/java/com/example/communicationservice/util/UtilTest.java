package com.example.communicationservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UtilTest {

  @Test
  void mask_nullValue_returnsAsterisks() {
    assertThat(Util.mask(null)).isEqualTo("***");
  }

  @Test
  void mask_email_masksLocalPartKeepingFirstTwoCharsAndDomain() {
    assertThat(Util.mask("johndoe@example.com")).isEqualTo("jo***@example.com");
  }

  @Test
  void mask_emailWithShortLocalPart_keepsWhateverIsAvailable() {
    assertThat(Util.mask("a@example.com")).isEqualTo("a***@example.com");
  }

  @Test
  void mask_emailWithTwoCharLocalPart_keepsBothChars() {
    assertThat(Util.mask("ab@example.com")).isEqualTo("ab***@example.com");
  }

  @Test
  void mask_nonEmailLongValue_keepsLastFourChars() {
    assertThat(Util.mask("+15005550006")).isEqualTo("****0006");
  }

  @Test
  void mask_nonEmailShortValue_returnsAsterisksOnly() {
    assertThat(Util.mask("123")).isEqualTo("****");
  }

  @Test
  void mask_nonEmailExactlyFourChars_returnsFullMaskedValue() {
    assertThat(Util.mask("1234")).isEqualTo("****1234");
  }

  @Test
  void mask_emptyString_returnsAsterisksOnly() {
    assertThat(Util.mask("")).isEqualTo("****");
  }
}
