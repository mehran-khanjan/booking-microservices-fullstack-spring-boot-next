package com.example.communicationservice.util;

/**
 * Utility class for masking sensitive strings (such as emails, passwords, etc.) for safe logging.
 *
 * <p>This class provides static methods to redact identifiable information in log output to comply
 * with privacy and security best practices.
 *
 * <p><b>Note:</b> This class is {@code final} and has a private constructor to prevent
 * instantiation.
 */
public final class Util {

  /** Private constructor to prevent instantiation of this utility class. */
  private Util() {
    // No instantiation
  }

  /**
   * Masks a given string value.
   *
   * <p>If the value contains an '@' symbol, it is treated as an email address: the local part is
   * reduced to its first two characters (or the whole local part if shorter) and the domain is kept
   * intact. Example: {@code "john.doe@example.com"} → {@code "jo***@example.com"}. <br>
   * If the value does not contain '@', the {@link #generalMask(String)} method is used, showing
   * only the last four characters.
   *
   * @param value the string to mask; may be {@code null}
   * @return the masked string, or {@code "***"} if {@code value} is {@code null}
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
   * Masks a non‑email string by keeping only the last four characters.
   *
   * <p>If the string is shorter than 4 characters, it is replaced with {@code "****"}.
   *
   * @param value the string to mask
   * @return the masked string
   */
  private static String generalMask(String value) {
    return value.length() < 4 ? "****" : "****" + value.substring(value.length() - 4);
  }
}
