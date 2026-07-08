package com.example.apigatewayservice.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTracingContextTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Nested
  @DisplayName("startFor")
  class StartFor {

    @Test
    @DisplayName("generates correlation and request IDs when absent")
    void generatesIdsWhenAbsent() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();

      try (RequestTracingContext ctx = RequestTracingContext.startFor(request, response)) {
        assertThat(ctx.getCorrelationId()).isNotBlank();
        assertThat(ctx.getRequestId()).isNotBlank();
        assertThat(UUID.fromString(ctx.getCorrelationId())).isNotNull();
        assertThat(UUID.fromString(ctx.getRequestId())).isNotNull();
        assertThat(ctx.getTransactionId()).isEmpty();
      }
    }

    @Test
    @DisplayName("reuses correlation, request, and transaction IDs already on the request")
    void reusesExistingIds() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Correlation-ID", "corr-123");
      request.addHeader("X-Request-ID", "req-456");
      request.addHeader("X-Transaction-ID", "txn-789");
      MockHttpServletResponse response = new MockHttpServletResponse();

      RequestTracingContext ctx = RequestTracingContext.startFor(request, response);

      assertThat(ctx.getCorrelationId()).isEqualTo("corr-123");
      assertThat(ctx.getRequestId()).isEqualTo("req-456");
      assertThat(ctx.getTransactionId()).contains("txn-789");
    }

    @Test
    @DisplayName("populates MDC with resolved IDs")
    void populatesMdc() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Correlation-ID", "corr-123");
      request.addHeader("X-Request-ID", "req-456");
      request.addHeader("X-Transaction-ID", "txn-789");
      MockHttpServletResponse response = new MockHttpServletResponse();

      RequestTracingContext.startFor(request, response);

      assertThat(MDC.get("correlationId")).isEqualTo("corr-123");
      assertThat(MDC.get("requestId")).isEqualTo("req-456");
      assertThat(MDC.get("transactionId")).isEqualTo("txn-789");
    }

    @Test
    @DisplayName("echoes correlation and request IDs onto the response")
    void echoesResponseHeaders() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Correlation-ID", "corr-123");
      request.addHeader("X-Request-ID", "req-456");
      MockHttpServletResponse response = new MockHttpServletResponse();

      RequestTracingContext.startFor(request, response);

      assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("corr-123");
      assertThat(response.getHeader("X-Request-ID")).isEqualTo("req-456");
    }

    @Test
    @DisplayName("close clears the entire MDC")
    void closeClearsMdc() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();

      RequestTracingContext ctx = RequestTracingContext.startFor(request, response);
      assertThat(MDC.get("correlationId")).isNotNull();

      ctx.close();

      assertThat(MDC.get("correlationId")).isNull();
      assertThat(MDC.get("requestId")).isNull();
    }
  }

  @Nested
  @DisplayName("wrapped request")
  class WrappedRequest {

    @Test
    @DisplayName("exposes tracing headers via getHeader, case-insensitively")
    void exposesTracingHeaders() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();

      RequestTracingContext ctx = RequestTracingContext.startFor(request, response);

      assertThat(ctx.getWrappedRequest().getHeader("X-Correlation-ID"))
          .isEqualTo(ctx.getCorrelationId());
      assertThat(ctx.getWrappedRequest().getHeader("x-correlation-id"))
          .isEqualTo(ctx.getCorrelationId());
    }

    @Test
    @DisplayName("falls back to original request for unrelated headers")
    void fallsBackForOtherHeaders() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Custom", "value");
      MockHttpServletResponse response = new MockHttpServletResponse();

      RequestTracingContext ctx = RequestTracingContext.startFor(request, response);

      assertThat(ctx.getWrappedRequest().getHeader("X-Custom")).isEqualTo("value");
    }

    @Test
    @DisplayName("getHeaders merges tracing value with any pre-existing values")
    void getHeadersMergesValues() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Correlation-ID", "corr-123");
      MockHttpServletResponse response = new MockHttpServletResponse();

      RequestTracingContext ctx = RequestTracingContext.startFor(request, response);

      List<String> values =
          Collections.list(ctx.getWrappedRequest().getHeaders("X-Correlation-ID"));
      assertThat(values).containsExactly("corr-123");
    }

    @Test
    @DisplayName("getHeaderNames includes tracing headers plus originals")
    void getHeaderNamesIncludesTracingHeaders() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Custom", "value");
      MockHttpServletResponse response = new MockHttpServletResponse();

      RequestTracingContext ctx = RequestTracingContext.startFor(request, response);

      List<String> names = Collections.list(ctx.getWrappedRequest().getHeaderNames());
      assertThat(names).contains("X-Correlation-ID", "X-Request-ID", "X-Custom");
    }

    @Test
    @DisplayName("does not include X-Transaction-ID header name when transaction id absent")
    void excludesTransactionIdWhenAbsent() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      MockHttpServletResponse response = new MockHttpServletResponse();

      RequestTracingContext ctx = RequestTracingContext.startFor(request, response);

      List<String> names = Collections.list(ctx.getWrappedRequest().getHeaderNames());
      assertThat(names).doesNotContain("X-Transaction-ID");
    }
  }
}
