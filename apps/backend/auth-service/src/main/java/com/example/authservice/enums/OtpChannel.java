package com.example.authservice.enums;

/**
 * Delivery channel for a one-time password (OTP).
 *
 * <p>This enum defines the supported channels through which an OTP can be delivered to a user,
 * currently either via EMAIL or PHONE (SMS). Each channel is associated with a Redis key prefix
 * used to store and retrieve OTP records, and also drives the dispatch mechanism (e.g., email
 * service vs. SMS service).
 *
 * <p>The Redis prefix is used to namespace OTP entries by channel, allowing separate storage and
 * lookup for email-based and phone-based OTPs.
 *
 * @author Your Team
 * @since 1.0
 */
public enum OtpChannel {
  /**
   * OTP delivered via email.
   *
   * <p>Redis keys for email OTPs will be prefixed with {@code "otp:email:"}.
   */
  EMAIL("otp:email:"),

  /**
   * OTP delivered via SMS to a phone number.
   *
   * <p>Redis keys for phone OTPs will be prefixed with {@code "otp:phone:"}.
   */
  PHONE("otp:phone:");

  /** The Redis key prefix used for OTP records of this channel. */
  private final String redisPrefix;

  /**
   * Constructs an {@code OtpChannel} with the specified Redis key prefix.
   *
   * @param redisPrefix the string to be used as a prefix for Redis keys for this channel
   */
  OtpChannel(String redisPrefix) {
    this.redisPrefix = redisPrefix;
  }

  /**
   * Returns the Redis key prefix associated with this channel.
   *
   * @return the Redis prefix (e.g., {@code "otp:email:"} for EMAIL)
   */
  public String prefix() {
    return redisPrefix;
  }
}
