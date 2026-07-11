package com.example.authservice.util;

import com.example.authservice.enums.OtpChannel;

/**
 * Utility class for masking sensitive strings (emails, phone numbers, etc.) for safe logging.
 *
 * <p>This class provides static methods to obscure personally identifiable information (PII) such
 * as email addresses and phone numbers before outputting them in logs, to comply with privacy
 * regulations (e.g., GDPR) and security best practices.
 *
 * <p>The masking strategies are:
 *
 * <ul>
 *   <li><strong>Email:</strong> Shows the first two characters of the local part and the full
 *       domain (e.g., {@code "john.doe@example.com"} → {@code "jo***@example.com"}).
 *   <li><strong>Phone or generic strings:</strong> Shows the last four digits, with the preceding
 *       characters replaced by "****". If the string is shorter than four characters, it becomes
 *       "****" (e.g., {@code "+1234567890"} → {@code "****7890"}).
 * </ul>
 *
 * <p>The {@link #otpMask(OtpChannel, String)} method selects the appropriate masking strategy based
 * on the {@link OtpChannel} – email uses email masking, while phone (and any other) uses generic
 * masking.
 *
 * <p>This class is final and has a private constructor to prevent instantiation.
 *
 * @author Your Team
 * @since 1.0
 */
public final class Util {

  /** Private constructor to prevent instantiation of this utility class. */
  private Util() {
    throw new UnsupportedOperationException("Utility class should not be instantiated");
  }

  /**
   * Masks a string based on its content type.
   *
   * <p>If the input contains an '@' symbol, it is treated as an email and masked using
   * email-specific rules (see class description). Otherwise, generic masking is applied.
   *
   * @param value the string to mask (may be {@code null})
   * @return the masked string; returns "***" if the input is {@code null}
   */
  public static String mask(String value) {
    if (value == null) return "***";
    if (value.contains("@")) {
      String[] parts = value.split("@");
      return parts[0].substring(0, Math.min(2, parts[0].length())) + "***@" + parts[1];
    }
    return generalMask(value);
  }

  /**
   * Masks an identifier based on the given OTP channel.
   *
   * <p>If the channel is {@link OtpChannel#EMAIL}, the identifier is masked using email-specific
   * rules (via {@link #mask(String)}). For any other channel (e.g., {@code PHONE}), generic masking
   * is applied.
   *
   * @param channel the OTP delivery channel (determines the masking strategy)
   * @param identifier the email address or phone number to mask
   * @return the masked identifier
   */
  public static String otpMask(OtpChannel channel, String identifier) {
    if (channel == OtpChannel.EMAIL) {
      return mask(identifier);
    }
    return generalMask(identifier);
  }

  /**
   * Applies generic masking to a string, showing only the last four characters.
   *
   * <p>If the string length is less than 4, it returns "****". Otherwise, it returns "****"
   * followed by the last four characters.
   *
   * @param value the string to mask (may be {@code null}; returns "***" if null)
   * @return the generically masked string
   */
  private static String generalMask(String value) {
    return value.length() < 4 ? "****" : "****" + value.substring(value.length() - 4);
  }
}
