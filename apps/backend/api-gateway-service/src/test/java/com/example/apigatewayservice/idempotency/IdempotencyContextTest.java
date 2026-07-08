package com.example.apigatewayservice.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IdempotencyContextTest {

  private IdempotencyValidator validator;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    validator = mock(IdempotencyValidator.class);
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
    @DisplayName("returns context with empty key when method doesn't require the check")
    void skipsWhenMethodDoesNotRequireCheck() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      when(validator.requiresIdempotencyCheck("GET")).thenReturn(false);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);

      assertThat(ctx).isNotNull();
      assertThat(ctx.getIdempotencyKey()).isEmpty();
      assertThat(MDC.get("idempotencyKey")).isNull();
    }

    @Test
    @DisplayName("returns null and sets 400 when key required but missing")
    void rejectsMissingKey() {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      when(validator.requiresIdempotencyCheck("POST")).thenReturn(true);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);

      assertThat(ctx).isNull();
      assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("returns null and sets 400 when key required but blank")
    void rejectsBlankKey() {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      request.addHeader(IdempotencyContext.IDEMPOTENCY_KEY_HEADER, "   ");
      when(validator.requiresIdempotencyCheck("POST")).thenReturn(true);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);

      assertThat(ctx).isNull();
      assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("returns null when key present but not a valid UUID")
    void rejectsInvalidUuid() {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      request.addHeader(IdempotencyContext.IDEMPOTENCY_KEY_HEADER, "not-a-uuid");
      when(validator.requiresIdempotencyCheck("POST")).thenReturn(true);
      when(validator.isValidUUID("not-a-uuid")).thenReturn(false);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);

      assertThat(ctx).isNull();
    }

    @Test
    @DisplayName("returns populated context and sets MDC when key is valid")
    void acceptsValidKey() {
      String key = "550e8400-e29b-41d4-a716-446655440000";
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      request.addHeader(IdempotencyContext.IDEMPOTENCY_KEY_HEADER, key);
      when(validator.requiresIdempotencyCheck("POST")).thenReturn(true);
      when(validator.isValidUUID(key)).thenReturn(true);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);

      assertThat(ctx).isNotNull();
      assertThat(ctx.getIdempotencyKey()).contains(key);
      assertThat(MDC.get("idempotencyKey")).isEqualTo(key);

      ctx.close();
      assertThat(MDC.get("idempotencyKey")).isNull();
    }
  }

  @Nested
  @DisplayName("wrapped request")
  class WrappedRequest {

    @Test
    @DisplayName("exposes the idempotency key via getHeader regardless of original headers")
    void wrappedRequestExposesKey() {
      String key = "550e8400-e29b-41d4-a716-446655440000";
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      when(validator.requiresIdempotencyCheck("POST")).thenReturn(true);
      when(validator.isValidUUID(key)).thenReturn(true);
      request.addHeader(IdempotencyContext.IDEMPOTENCY_KEY_HEADER, key);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);
      assertThat(ctx).isNotNull();

      assertThat(ctx.getWrappedRequest().getHeader("Idempotency-Key")).isEqualTo(key);
      // case-insensitive match
      assertThat(ctx.getWrappedRequest().getHeader("idempotency-key")).isEqualTo(key);
    }

    @Test
    @DisplayName("getHeader falls back to original request for unrelated headers")
    void wrappedRequestFallsBackForOtherHeaders() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Custom", "value");
      when(validator.requiresIdempotencyCheck("GET")).thenReturn(false);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);

      assertThat(ctx.getWrappedRequest().getHeader("X-Custom")).isEqualTo("value");
      assertThat(ctx.getWrappedRequest().getHeader("Idempotency-Key")).isNull();
    }

    @Test
    @DisplayName("getHeaders merges the synthesized key ahead of any original values")
    void getHeadersMergesValues() {
      String key = "550e8400-e29b-41d4-a716-446655440000";
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      when(validator.requiresIdempotencyCheck("POST")).thenReturn(true);
      when(validator.isValidUUID(key)).thenReturn(true);
      request.addHeader(IdempotencyContext.IDEMPOTENCY_KEY_HEADER, key);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);

      List<String> values = Collections.list(ctx.getWrappedRequest().getHeaders("Idempotency-Key"));
      assertThat(values).containsExactly(key);
    }

    @Test
    @DisplayName("getHeaders falls back to original when key absent")
    void getHeadersFallsBackWhenAbsent() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Custom", "a");
      request.addHeader("X-Custom", "b");
      when(validator.requiresIdempotencyCheck("GET")).thenReturn(false);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);

      List<String> values = Collections.list(ctx.getWrappedRequest().getHeaders("X-Custom"));
      assertThat(values).containsExactly("a", "b");
    }

    @Test
    @DisplayName("getHeaderNames includes the idempotency header plus originals")
    void getHeaderNamesIncludesSynthesizedHeader() {
      String key = "550e8400-e29b-41d4-a716-446655440000";
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resource");
      request.addHeader("X-Custom", "value");
      when(validator.requiresIdempotencyCheck("POST")).thenReturn(true);
      when(validator.isValidUUID(key)).thenReturn(true);
      request.addHeader(IdempotencyContext.IDEMPOTENCY_KEY_HEADER, key);

      IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);

      List<String> names = Collections.list(ctx.getWrappedRequest().getHeaderNames());
      assertThat(names).contains("Idempotency-Key", "X-Custom");
    }
  }

  @Test
  @DisplayName("close() is a no-op when no key was set")
  void closeIsNoopWithoutKey() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
    when(validator.requiresIdempotencyCheck("GET")).thenReturn(false);

    IdempotencyContext ctx = IdempotencyContext.validateFor(request, response, validator);
    ctx.close();

    assertThat(MDC.get("idempotencyKey")).isNull();
  }

  @Test
  @DisplayName("does not call validator methods when method does not require check")
  void doesNotCallIsValidUuidWhenSkipped() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
    when(validator.requiresIdempotencyCheck("GET")).thenReturn(false);

    IdempotencyContext.validateFor(request, response, validator);

    org.mockito.Mockito.verify(validator, org.mockito.Mockito.never()).isValidUUID(anyString());
  }
}
