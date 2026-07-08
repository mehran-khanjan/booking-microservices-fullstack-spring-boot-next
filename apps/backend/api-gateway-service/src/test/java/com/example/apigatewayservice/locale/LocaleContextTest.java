package com.example.apigatewayservice.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LocaleContextTest {

  private LocaleValidator validator;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    validator = mock(LocaleValidator.class);
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Nested
  @DisplayName("validateFor")
  class ValidateFor {

    @Test
    @DisplayName("returns null when header is required but missing")
    void rejectsMissingRequiredHeader() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      when(validator.isRequired()).thenReturn(true);

      LocaleContext ctx = LocaleContext.validateFor(request, response, validator);

      assertThat(ctx).isNull();
    }

    @Test
    @DisplayName("uses default locale when header optional and missing")
    void usesDefaultWhenOptionalAndMissing() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      when(validator.isRequired()).thenReturn(false);
      when(validator.getDefaultLocale()).thenReturn("en-US");
      when(validator.isValid("en-US")).thenReturn(true);
      when(validator.extractPrimaryLanguage("en-US")).thenReturn("en-US");

      LocaleContext ctx = LocaleContext.validateFor(request, response, validator);

      assertThat(ctx).isNotNull();
      assertThat(ctx.getAcceptLanguage()).isEqualTo("en-US");
      assertThat(ctx.getPrimaryLanguage()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("returns null when header present but fails validation")
    void rejectsInvalidHeader() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("Accept-Language", "garbage");
      when(validator.isRequired()).thenReturn(true);
      when(validator.isValid("garbage")).thenReturn(false);

      LocaleContext ctx = LocaleContext.validateFor(request, response, validator);

      assertThat(ctx).isNull();
    }

    @Test
    @DisplayName("returns populated context, applies MDC, and echoes Content-Language on success")
    void acceptsValidHeader() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("Accept-Language", "fr-FR");
      when(validator.isRequired()).thenReturn(true);
      when(validator.isValid("fr-FR")).thenReturn(true);
      when(validator.extractPrimaryLanguage("fr-FR")).thenReturn("fr-FR");

      LocaleContext ctx = LocaleContext.validateFor(request, response, validator);

      assertThat(ctx).isNotNull();
      assertThat(ctx.getAcceptLanguage()).isEqualTo("fr-FR");
      assertThat(ctx.getPrimaryLanguage()).isEqualTo("fr-FR");
      assertThat(MDC.get("acceptLanguage")).isEqualTo("fr-FR");
      assertThat(MDC.get("primaryLanguage")).isEqualTo("fr-FR");
      assertThat(response.getHeader("Content-Language")).isEqualTo("fr-FR");

      ctx.close();
      assertThat(MDC.get("acceptLanguage")).isNull();
      assertThat(MDC.get("primaryLanguage")).isNull();
    }
  }

  @Nested
  @DisplayName("wrapped request")
  class WrappedRequest {

    private LocaleContext validContext(MockHttpServletRequest request, String header) {
      when(validator.isRequired()).thenReturn(true);
      when(validator.isValid(header)).thenReturn(true);
      when(validator.extractPrimaryLanguage(header)).thenReturn(header);
      request.addHeader("Accept-Language", header);
      return LocaleContext.validateFor(request, response, validator);
    }

    @Test
    @DisplayName("getHeader returns the accept-language value case-insensitively")
    void getHeaderCaseInsensitive() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      LocaleContext ctx = validContext(request, "en-US");

      assertThat(ctx.getWrappedRequest().getHeader("Accept-Language")).isEqualTo("en-US");
      assertThat(ctx.getWrappedRequest().getHeader("accept-language")).isEqualTo("en-US");
    }

    @Test
    @DisplayName("getHeader falls back to original request for unrelated headers")
    void getHeaderFallsBackForOtherHeaders() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Custom", "value");
      LocaleContext ctx = validContext(request, "en-US");

      assertThat(ctx.getWrappedRequest().getHeader("X-Custom")).isEqualTo("value");
    }

    @Test
    @DisplayName("getHeaders returns merged list containing locale value first")
    void getHeadersMergesValues() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      LocaleContext ctx = validContext(request, "en-US");

      List<String> values = Collections.list(ctx.getWrappedRequest().getHeaders("Accept-Language"));
      assertThat(values).containsExactly("en-US");
    }

    @Test
    @DisplayName("getHeaderNames includes Accept-Language plus originals")
    void getHeaderNamesIncludesLocaleHeader() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Custom", "value");
      LocaleContext ctx = validContext(request, "en-US");

      List<String> names = Collections.list(ctx.getWrappedRequest().getHeaderNames());
      assertThat(names).contains("Accept-Language", "X-Custom");
    }

    @Test
    @DisplayName("getLocale parses language and country from Accept-Language")
    void getLocaleParsesLanguageAndCountry() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      LocaleContext ctx = validContext(request, "fr-FR");

      Locale locale = ctx.getWrappedRequest().getLocale();
      assertThat(locale.getLanguage()).isEqualTo("fr");
      assertThat(locale.getCountry()).isEqualTo("FR");
    }

    @Test
    @DisplayName("getLocale parses language-only tags")
    void getLocaleParsesLanguageOnly() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      LocaleContext ctx = validContext(request, "de");

      Locale locale = ctx.getWrappedRequest().getLocale();
      assertThat(locale.getLanguage()).isEqualTo("de");
    }

    @Test
    @DisplayName("getLocales returns a singleton enumeration of the parsed locale")
    void getLocalesReturnsSingleton() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      LocaleContext ctx = validContext(request, "en-US");

      List<Locale> locales = Collections.list(ctx.getWrappedRequest().getLocales());
      assertThat(locales).hasSize(1);
      assertThat(locales.get(0).getLanguage()).isEqualTo("en");
    }
  }
}
