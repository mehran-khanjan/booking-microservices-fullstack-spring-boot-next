package com.example.apigatewayservice.locale;

import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates the {@code Accept-Language} header format and content.
 *
 * <p>Supports both simple language tags (e.g., "en-US") and quality‑value lists (e.g.,
 * "en-US,en;q=0.9,fr;q=0.8"). Also checks against configured supported languages and locales.
 */
@Component
public class LocaleValidator {

  private static final Logger log = LoggerFactory.getLogger(LocaleValidator.class);

  // Matches valid language tags: en, en-US, zh-CN, etc.
  // Supports ISO 639-1 language codes with optional ISO 3166-1 country codes
  private static final Pattern LANGUAGE_TAG_PATTERN = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

  // Matches Accept-Language header with optional quality values
  // Example: en-US,en;q=0.9,fr;q=0.8
  private static final Pattern ACCEPT_LANGUAGE_PATTERN =
      Pattern.compile(
          "^[a-z]{2}(-[A-Z]{2})?(;q=[0-9]+(\\.[0-9]+)?)?(\\s*,\\s*[a-z]{2}(-[A-Z]{2})?(;q=[0-9]+(\\.[0-9]+)?)?)*$");

  private final LocaleProperties properties;

  public LocaleValidator(LocaleProperties properties) {
    this.properties = properties;
  }

  /** Validates the Accept-Language header value. Returns true if valid, false otherwise. */
  public boolean isValid(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      log.debug("Accept-Language header is null or blank");
      return false;
    }

    // Validate full header format (optional, but keep for extra safety)
    if (!ACCEPT_LANGUAGE_PATTERN.matcher(acceptLanguage.trim()).matches()) {
      log.warn("Accept-Language header has invalid format: {}", acceptLanguage);
      return false;
    }

    String primaryLanguage = extractPrimaryLanguage(acceptLanguage);

    // ★ NEW: Ensure the primary language tag itself is a valid BCP-47 tag
    if (!isValidLanguageTag(primaryLanguage)) {
      log.warn("Primary language tag '{}' is not a valid BCP-47 tag", primaryLanguage);
      return false;
    }

    if (log.isTraceEnabled()) {
      log.trace("Primary language extracted from '{}': '{}'", acceptLanguage, primaryLanguage);
    }

    // Validate against supported languages/locales if configured
    if (!properties.getSupportedLanguages().isEmpty()
        || !properties.getSupportedLocales().isEmpty()) {
      return isSupportedLanguage(primaryLanguage);
    }

    return true;
  }

  /**
   * Extracts the primary language tag from Accept-Language header. Example:
   * "en-US,en;q=0.9,fr;q=0.8" -> "en-US"
   */
  public String extractPrimaryLanguage(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return null;
    }

    String trimmed = acceptLanguage.trim();

    // Remove quality values if present
    String withoutQuality = trimmed.split(";")[0].trim();

    // Get first language if comma-separated list
    String primary = withoutQuality.split(",")[0].trim();

    if (log.isDebugEnabled()) {
      log.debug("Extracted primary language '{}' from '{}'", primary, acceptLanguage);
    }

    return primary;
  }

  /** Validates language tag format (e.g., "en", "en-US"). */
  public boolean isValidLanguageTag(String languageTag) {
    if (languageTag == null || languageTag.isBlank()) {
      return false;
    }

    boolean valid = LANGUAGE_TAG_PATTERN.matcher(languageTag.trim()).matches();

    if (log.isTraceEnabled()) {
      log.trace("Language tag '{}' validation result: {}", languageTag, valid);
    }

    return valid;
  }

  /** Checks if the language is in the supported list. */
  private boolean isSupportedLanguage(String languageTag) {
    if (languageTag == null || languageTag.isBlank()) {
      return false;
    }

    String normalized = languageTag.trim();

    // Check full locale first (e.g., "en-US")
    if (properties.getSupportedLocales().contains(normalized)) {
      log.debug("Language '{}' matched supported locale", normalized);
      return true;
    }

    // Extract language code (e.g., "en" from "en-US")
    String languageCode = normalized.split("-")[0];

    if (properties.getSupportedLanguages().contains(languageCode)) {
      log.debug("Language code '{}' matched supported language", languageCode);
      return true;
    }

    log.warn(
        "Language '{}' is not in supported languages {} or locales {}",
        normalized,
        properties.getSupportedLanguages(),
        properties.getSupportedLocales());

    return false;
  }

  /** Parses Accept-Language header into a Java Locale object. Returns null if parsing fails. */
  public Locale parseLocale(String acceptLanguage) {
    String primary = extractPrimaryLanguage(acceptLanguage);

    if (primary == null || !isValidLanguageTag(primary)) {
      return null;
    }

    try {
      String[] parts = primary.split("-");

      if (parts.length == 2) {
        // Language + Country: en-US
        return new Locale(parts[0].toLowerCase(), parts[1].toUpperCase());
      } else {
        // Language only: en
        return new Locale(parts[0].toLowerCase());
      }
    } catch (Exception e) {
      log.error("Failed to parse locale from '{}': {}", primary, e.getMessage());
      return null;
    }
  }

  /** Returns the default locale from configuration. */
  public String getDefaultLocale() {
    return properties.getDefaultLocale();
  }

  /** Checks if Accept-Language validation is enabled. */
  public boolean isEnabled() {
    return properties.isEnabled();
  }

  /** Checks if Accept-Language header is required. */
  public boolean isRequired() {
    return properties.isRequired();
  }
}
