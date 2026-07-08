package com.example.authservice.util;

/** Utility class for masking sensitive strings (emails, passwords) for safe logging. */
public final class Util {

  public static String mask(String value) {
    if (value == null) return "***";
    if (value.contains("@")) {
      String[] parts = value.split("@");
      return parts[0].substring(0, Math.min(2, parts[0].length())) + "***@" + parts[1];
    }
    return value.length() < 4 ? "****" : "****" + value.substring(value.length() - 4);
  }
}
