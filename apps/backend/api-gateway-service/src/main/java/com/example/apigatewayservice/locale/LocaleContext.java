package com.example.apigatewayservice.locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Immutable per‑request context that validates and provides the {@code Accept-Language} header.
 *
 * <ul>
 *   <li>Validates the header format and content
 *   <li>Injects the locale into MDC for structured logging
 *   <li>Wraps the request to expose the locale downstream
 *   <li>Echoes the primary language back in the {@code Content-Language} response header
 *   <li>Auto‑clears MDC via {@link AutoCloseable}
 * </ul>
 *
 * <p>Not a Spring bean – instantiated per‑request to avoid shared state.
 */
public final class LocaleContext implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(LocaleContext.class);

  public static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
  public static final String CONTENT_LANGUAGE_HEADER = "Content-Language";

  private final String acceptLanguage;
  private final String primaryLanguage;
  private final LocaleRequestWrapper wrappedRequest;

  private LocaleContext(HttpServletRequest request, String acceptLanguage, String primaryLanguage) {
    this.acceptLanguage = Objects.requireNonNull(acceptLanguage, "acceptLanguage");
    this.primaryLanguage = Objects.requireNonNull(primaryLanguage, "primaryLanguage");
    this.wrappedRequest = new LocaleRequestWrapper(request, acceptLanguage);

    if (log.isDebugEnabled()) {
      log.debug(
          "LocaleContext created for request: {} {}, acceptLanguage={}, primaryLanguage={}",
          request.getMethod(),
          request.getRequestURI(),
          acceptLanguage,
          primaryLanguage);
    }
  }

  /**
   * Factory method: validates Accept-Language header.
   *
   * @param request the original HTTP request
   * @param response the HTTP response (used to set error status on failure)
   * @param validator the locale validator
   * @return a new context if validation passes, or {@code null} if validation fails
   */
  public static LocaleContext validateFor(
      HttpServletRequest request, HttpServletResponse response, LocaleValidator validator) {

    String acceptLanguage = request.getHeader(ACCEPT_LANGUAGE_HEADER);

    // Check if header is required
    if (validator.isRequired()) {
      if (acceptLanguage == null || acceptLanguage.isBlank()) {
        log.warn("Accept-Language header is required but not provided");
        return null; // Validation failed
      }
    } else {
      // Header is optional - use default if not provided
      if (acceptLanguage == null || acceptLanguage.isBlank()) {
        String defaultLocale = validator.getDefaultLocale();
        log.info("Accept-Language not provided - using default: {}", defaultLocale);
        acceptLanguage = defaultLocale;
      }
    }

    // Validate format and content
    if (!validator.isValid(acceptLanguage)) {
      log.warn("Invalid Accept-Language header: {}", acceptLanguage);
      return null; // Validation failed
    }

    String primaryLanguage = validator.extractPrimaryLanguage(acceptLanguage);

    log.info("Valid Accept-Language header: {} (primary: {})", acceptLanguage, primaryLanguage);

    LocaleContext ctx = new LocaleContext(request, acceptLanguage, primaryLanguage);
    ctx.applyToMdc();
    ctx.echoOnResponse(response);

    return ctx;
  }

  /** Returns the wrapped request with locale accessible. Use this in your filter chain. */
  public HttpServletRequest getWrappedRequest() {
    if (log.isTraceEnabled()) {
      log.trace("Returning wrapped request with locale context");
    }
    return wrappedRequest;
  }

  public String getAcceptLanguage() {
    return acceptLanguage;
  }

  public String getPrimaryLanguage() {
    return primaryLanguage;
  }

  private void applyToMdc() {
    MDC.put("acceptLanguage", acceptLanguage);
    MDC.put("primaryLanguage", primaryLanguage);

    if (log.isDebugEnabled()) {
      log.debug(
          "MDC populated with acceptLanguage={}, primaryLanguage={}",
          acceptLanguage,
          primaryLanguage);
    }
  }

  private void echoOnResponse(HttpServletResponse response) {
    // Echo back the primary language in Content-Language header
    response.setHeader(CONTENT_LANGUAGE_HEADER, primaryLanguage);

    if (log.isDebugEnabled()) {
      log.debug("Set Content-Language response header: {}", primaryLanguage);
    }
  }

  /**
   * Clears MDC locale to prevent thread-pool pollution. Called automatically when used in
   * try-with-resources.
   */
  @Override
  public void close() {
    if (log.isDebugEnabled()) {
      log.debug("Removing locale from MDC: acceptLanguage={}", acceptLanguage);
    }
    MDC.remove("acceptLanguage");
    MDC.remove("primaryLanguage");
  }

  /**
   * Inner request wrapper that provides access to locale. Handles case-insensitive header matching
   * per HTTP spec.
   */
  private static final class LocaleRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> localeHeaders;

    LocaleRequestWrapper(HttpServletRequest request, String acceptLanguage) {
      super(request);
      this.localeHeaders = new HashMap<>(1);
      if (acceptLanguage != null && !acceptLanguage.isBlank()) {
        localeHeaders.put(ACCEPT_LANGUAGE_HEADER, acceptLanguage);
      }

      if (log.isTraceEnabled()) {
        log.trace("LocaleRequestWrapper created with headers: {}", localeHeaders);
      }
    }

    @Override
    public String getHeader(String name) {
      String localeValue = getLocaleHeader(name);
      if (localeValue != null) {
        if (log.isTraceEnabled()) {
          log.trace("getHeader('{}') returning locale value: {}", name, localeValue);
        }
        return localeValue;
      }
      String original = super.getHeader(name);
      if (log.isTraceEnabled()) {
        log.trace("getHeader('{}') returning original value: {}", name, original);
      }
      return original;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      String localeValue = getLocaleHeader(name);
      if (localeValue != null) {
        List<String> values = new ArrayList<>();
        values.add(localeValue);
        Enumeration<String> originalHeaders = super.getHeaders(name);
        while (originalHeaders.hasMoreElements()) {
          String original = originalHeaders.nextElement();
          if (!localeValue.equals(original)) {
            values.add(original);
          }
        }
        if (log.isTraceEnabled()) {
          log.trace("getHeaders('{}') returning merged values: {}", name, values);
        }
        return Collections.enumeration(values);
      }
      Enumeration<String> original = super.getHeaders(name);
      if (log.isTraceEnabled()) {
        log.trace("getHeaders('{}') returning original enumeration", name);
      }
      return original;
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      Set<String> names = new LinkedHashSet<>();
      names.addAll(localeHeaders.keySet());
      Enumeration<String> originalNames = super.getHeaderNames();
      while (originalNames.hasMoreElements()) {
        names.add(originalNames.nextElement());
      }
      if (log.isTraceEnabled()) {
        log.trace("getHeaderNames() returning: {}", names);
      }
      return Collections.enumeration(names);
    }

    @Override
    public Locale getLocale() {
      String acceptLang = localeHeaders.get(ACCEPT_LANGUAGE_HEADER);
      if (acceptLang != null) {
        try {
          String primary = acceptLang.split(",")[0].split(";")[0].trim();
          String[] parts = primary.split("-");
          if (parts.length == 2) {
            return new Locale(parts[0].toLowerCase(), parts[1].toUpperCase());
          } else {
            return new Locale(parts[0].toLowerCase());
          }
        } catch (Exception e) {
          log.warn("Failed to parse locale from Accept-Language: {}", acceptLang);
        }
      }
      return super.getLocale();
    }

    @Override
    public Enumeration<Locale> getLocales() {
      Locale locale = getLocale();
      return Collections.enumeration(Collections.singletonList(locale));
    }

    private String getLocaleHeader(String name) {
      if (name == null) return null;
      for (Map.Entry<String, String> entry : localeHeaders.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue();
        }
      }
      return null;
    }
  }
}
