package com.example.apigatewayservice.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.apigatewayservice.idempotency.IdempotencyProperties;
import com.example.apigatewayservice.idempotency.IdempotencyValidator;
import com.example.apigatewayservice.locale.LocaleProperties;
import com.example.apigatewayservice.locale.LocaleValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayEdgeFilterTest {

  private IdempotencyValidator idempotencyValidator;
  private IdempotencyProperties idempotencyProperties;
  private LocaleValidator localeValidator;
  private LocaleProperties localeProperties;
  private ObjectMapper objectMapper;
  private GatewayEdgeFilter filter;

  @BeforeEach
  void setUp() {
    idempotencyValidator = mock(IdempotencyValidator.class);
    idempotencyProperties = new IdempotencyProperties();
    localeValidator = mock(LocaleValidator.class);
    localeProperties = new LocaleProperties();
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    filter =
        new GatewayEdgeFilter(
            idempotencyValidator,
            idempotencyProperties,
            localeValidator,
            localeProperties,
            objectMapper);
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  /** Simple FilterChain stub that records whether it was invoked and what request it saw. */
  private static class RecordingFilterChain implements FilterChain {
    boolean invoked = false;
    HttpServletRequest seenRequest;
    int statusToSet = 200;
    RuntimeException exceptionToThrow;

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
        throws IOException, ServletException {
      invoked = true;
      seenRequest = (HttpServletRequest) request;
      if (exceptionToThrow != null) {
        throw exceptionToThrow;
      }
      ((HttpServletResponse) response).setStatus(statusToSet);
    }
  }

  @Nested
  @DisplayName("diagnostic paths")
  class DiagnosticPaths {

    @Test
    @DisplayName("skips locale and idempotency validation for /actuator paths")
    void skipsValidationForActuator() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isTrue();
      assertThat(response.getHeader("X-Correlation-ID")).isNotBlank();
      verify(localeValidator, never()).isValid(anyString());
      verify(idempotencyValidator, never()).isValidUUID(anyString());
    }

    @Test
    @DisplayName("skips validation for /swagger and /v3/api-docs prefixes")
    void skipsValidationForSwaggerAndApiDocs() throws Exception {
      for (String path : new String[] {"/swagger/ui.html", "/v3/api-docs"}) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
      }
    }
  }

  @Nested
  @DisplayName("locale validation disabled")
  class LocaleDisabled {

    @Test
    @DisplayName("bypasses locale validation entirely when disabled")
    void bypassesLocaleWhenDisabled() throws Exception {
      localeProperties.setEnabled(false);
      idempotencyProperties.setEnabled(false);

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isTrue();
      verify(localeValidator, never()).isValid(anyString());
    }
  }

  @Nested
  @DisplayName("locale validation")
  class LocaleValidation {

    @Test
    @DisplayName("returns 400 with JSON body when Accept-Language is required but missing")
    void rejectsWhenLocaleRequiredAndMissing() throws Exception {
      idempotencyProperties.setEnabled(false);
      when(localeValidator.isRequired()).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isFalse();
      assertThat(response.getStatus()).isEqualTo(400);
      // Content type may include charset, so use startsWith
      assertThat(response.getContentType()).startsWith("application/json");
      assertThat(response.getContentAsString())
          .contains("Accept-Language header is required but not provided");
    }

    @Test
    @DisplayName("includes supported languages/locales in the error detail when configured")
    void includesSupportedListsInErrorDetail() throws Exception {
      idempotencyProperties.setEnabled(false);
      localeProperties.setSupportedLanguages(java.util.Set.of("en"));
      localeProperties.setSupportedLocales(java.util.Set.of("en-US"));
      when(localeValidator.isRequired()).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      String body = response.getContentAsString();
      assertThat(body).contains("Supported languages");
      assertThat(body).contains("Supported locales");
    }

    @Test
    @DisplayName("proceeds down the chain when Accept-Language is valid")
    void proceedsWhenLocaleValid() throws Exception {
      idempotencyProperties.setEnabled(false);
      when(localeValidator.isRequired()).thenReturn(true);
      when(localeValidator.isValid("en-US")).thenReturn(true);
      when(localeValidator.extractPrimaryLanguage("en-US")).thenReturn("en-US");

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("Accept-Language", "en-US");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isTrue();
      assertThat(chain.seenRequest.getHeader("Accept-Language")).isEqualTo("en-US");
      assertThat(response.getHeader("Content-Language")).isEqualTo("en-US");
    }
  }

  @Nested
  @DisplayName("idempotency validation")
  class IdempotencyValidation {

    @Test
    @DisplayName("returns 400 when Idempotency-Key required but missing")
    void rejectsWhenKeyMissing() throws Exception {
      localeProperties.setEnabled(false);
      when(idempotencyValidator.requiresIdempotencyCheck("POST")).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isFalse();
      assertThat(response.getStatus()).isEqualTo(400);
      assertThat(response.getContentAsString()).contains("Invalid Idempotency-Key format");
    }

    @Test
    @DisplayName("returns 400 when Idempotency-Key is not a valid UUID")
    void rejectsWhenKeyInvalid() throws Exception {
      localeProperties.setEnabled(false);
      when(idempotencyValidator.requiresIdempotencyCheck("POST")).thenReturn(true);
      when(idempotencyValidator.isValidUUID("bad-key")).thenReturn(false);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      request.addHeader("Idempotency-Key", "bad-key");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isFalse();
      assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("proceeds down the chain when Idempotency-Key is valid")
    void proceedsWhenKeyValid() throws Exception {
      localeProperties.setEnabled(false);
      String key = "550e8400-e29b-41d4-a716-446655440000";
      when(idempotencyValidator.requiresIdempotencyCheck("POST")).thenReturn(true);
      when(idempotencyValidator.isValidUUID(key)).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      request.addHeader("Idempotency-Key", key);
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isTrue();
      assertThat(chain.seenRequest.getHeader("Idempotency-Key")).isEqualTo(key);
    }

    @Test
    @DisplayName("skips idempotency validation when disabled globally")
    void skipsWhenDisabledGlobally() throws Exception {
      localeProperties.setEnabled(false);
      idempotencyProperties.setEnabled(false);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isTrue();
      verify(idempotencyValidator, never()).isValidUUID(anyString());
    }

    @Test
    @DisplayName("skips idempotency validation for GET requests")
    void skipsForGetMethod() throws Exception {
      localeProperties.setEnabled(false);
      when(idempotencyValidator.requiresIdempotencyCheck("GET")).thenReturn(false);

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isTrue();
    }
  }

  @Nested
  @DisplayName("exception propagation and completion logging")
  class ExceptionAndLogging {

    @Test
    @DisplayName("rethrows exceptions raised by downstream filter chain")
    void rethrowsDownstreamException() {
      localeProperties.setEnabled(false);
      idempotencyProperties.setEnabled(false);

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();
      chain.exceptionToThrow = new RuntimeException("downstream failure");

      assertThatThrownBy(() -> filter.doFilter(request, response, chain))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("downstream failure");
    }

    @Test
    @DisplayName("rethrows exceptions raised by downstream filter chain with idempotency enabled")
    void rethrowsDownstreamExceptionWithIdempotencyLayer() {
      localeProperties.setEnabled(false);
      String key = "550e8400-e29b-41d4-a716-446655440000";
      when(idempotencyValidator.requiresIdempotencyCheck("POST")).thenReturn(true);
      when(idempotencyValidator.isValidUUID(key)).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      request.addHeader("Idempotency-Key", key);
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();
      chain.exceptionToThrow = new RuntimeException("downstream failure");

      assertThatThrownBy(() -> filter.doFilter(request, response, chain))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("completes normally and logs for a server error status (>=500)")
    void handlesServerErrorStatus() throws Exception {
      localeProperties.setEnabled(false);
      idempotencyProperties.setEnabled(false);

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();
      chain.statusToSet = 500;

      filter.doFilter(request, response, chain);

      assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("completes normally and logs for a client error status (400-499)")
    void handlesClientErrorStatus() throws Exception {
      localeProperties.setEnabled(false);
      idempotencyProperties.setEnabled(false);

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();
      chain.statusToSet = 404;

      filter.doFilter(request, response, chain);

      assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("does not log completion for diagnostic paths when idempotency layer is skipped")
    void diagnosticPathSkipsCompletionLoggingBranch() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
      MockHttpServletResponse response = new MockHttpServletResponse();
      RecordingFilterChain chain = new RecordingFilterChain();
      chain.statusToSet = 200;

      filter.doFilter(request, response, chain);

      assertThat(chain.invoked).isTrue();
    }
  }

  @Test
  @DisplayName("forwarded headers are synthesized ahead of locale/idempotency layers")
  void synthesizesForwardedHeadersBeforeOtherLayers() throws Exception {
    localeProperties.setEnabled(false);
    idempotencyProperties.setEnabled(false);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
    request.setRemoteAddr("10.1.1.1");
    request.setScheme("https");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingFilterChain chain = new RecordingFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.invoked).isTrue();
    assertThat(chain.seenRequest.getHeader("X-Forwarded-For")).isEqualTo("10.1.1.1");
    assertThat(chain.seenRequest.getHeader("X-Forwarded-Proto")).isEqualTo("https");
  }
}
