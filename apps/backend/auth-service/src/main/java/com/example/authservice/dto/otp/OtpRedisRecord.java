package com.example.authservice.dto.otp;

/**
 * Value object representing an OTP entry stored in Redis.
 *
 * <p>Records the OTP code, the associated user ID, and the number of verification attempts made.
 * The {@link #withIncrementedAttempts()} method produces a new instance with the attempts counter
 * incremented by one.
 *
 * @param otp the one-time password code
 * @param userId the ID of the user for whom this OTP was generated
 * @param attempts the number of times this OTP has been submitted for verification
 */
public record OtpRedisRecord(String otp, String userId, int attempts) {

  /**
   * Creates a copy of this record with the attempts counter incremented by one.
   *
   * @return a new {@code OtpRedisRecord} with the same OTP and userId but {@code attempts + 1}
   */
  public OtpRedisRecord withIncrementedAttempts() {
    return new OtpRedisRecord(otp, userId, attempts + 1);
  }
}
