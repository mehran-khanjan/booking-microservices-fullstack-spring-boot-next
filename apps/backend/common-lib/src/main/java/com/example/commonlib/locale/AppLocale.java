package com.example.commonlib.locale;

import java.util.Set;

/**
 * Single source of truth for all locale‑related constants.
 *
 * <p>Used by validation, routing, logging, and downstream services.
 */
public final class AppLocale {

  // Individual locale string constants (use these in controllers/filters)
  public static final String LOCALE_EN_US = "en-US";
  public static final String LOCALE_FA = "fa";

  // The master set used for validation
  public static final Set<String> SUPPORTED_LOCALES = Set.of(LOCALE_EN_US, LOCALE_FA);

  // Default fallback
  public static final String DEFAULT_LOCALE = LOCALE_EN_US;

  private AppLocale() {
    // Prevent instantiation (static utility class)
    throw new UnsupportedOperationException("This is a constants class");
  }
}
