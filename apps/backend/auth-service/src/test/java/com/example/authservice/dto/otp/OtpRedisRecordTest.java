package com.example.authservice.dto.otp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OtpRedisRecordTest {

  @Test
  void accessors_returnConstructorValues() {
    OtpRedisRecord record = new OtpRedisRecord("123456", "user-id", 2);

    assertThat(record.otp()).isEqualTo("123456");
    assertThat(record.userId()).isEqualTo("user-id");
    assertThat(record.attempts()).isEqualTo(2);
  }

  @Test
  void withIncrementedAttempts_returnsNewRecord_withAttemptsPlusOne_andSameOtpAndUserId() {
    OtpRedisRecord record = new OtpRedisRecord("123456", "user-id", 2);

    OtpRedisRecord incremented = record.withIncrementedAttempts();

    assertThat(incremented.attempts()).isEqualTo(3);
    assertThat(incremented.otp()).isEqualTo("123456");
    assertThat(incremented.userId()).isEqualTo("user-id");
    assertThat(record.attempts()).isEqualTo(2); // original untouched (immutable record)
  }
}
