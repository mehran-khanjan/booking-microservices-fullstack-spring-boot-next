package com.example.authservice.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OtpChannelTest {

  @Test
  void email_hasEmailRedisPrefix() {
    assertThat(OtpChannel.EMAIL.prefix()).isEqualTo("otp:email:");
  }

  @Test
  void phone_hasPhoneRedisPrefix() {
    assertThat(OtpChannel.PHONE.prefix()).isEqualTo("otp:phone:");
  }

  @Test
  void hasExactlyTwoChannels() {
    assertThat(OtpChannel.values()).containsExactly(OtpChannel.EMAIL, OtpChannel.PHONE);
  }
}
