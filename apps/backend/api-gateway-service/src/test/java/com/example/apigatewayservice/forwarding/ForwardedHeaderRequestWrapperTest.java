package com.example.apigatewayservice.forwarding;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ForwardedHeaderRequestWrapperTest {

  @Nested
  @DisplayName("wrap")
  class Wrap {

    @Test
    @DisplayName("returns the original request unchanged when all headers already present")
    void returnsOriginalWhenAllHeadersPresent() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Forwarded-For", "1.2.3.4");
      request.addHeader("X-Forwarded-Proto", "https");
      request.addHeader("X-Forwarded-Host", "example.com");
      request.addHeader("Forwarded", "for=1.2.3.4;proto=https;host=example.com");

      HttpServletRequest wrapped = ForwardedHeaderRequestWrapper.wrap(request);

      assertThat(wrapped).isSameAs(request);
    }

    @Test
    @DisplayName("synthesizes missing headers using client IP, scheme, and host")
    void synthesizesMissingHeaders() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.setRemoteAddr("10.0.0.5");
      request.setScheme("https");
      request.setServerName("gateway.internal");

      HttpServletRequest wrapped = ForwardedHeaderRequestWrapper.wrap(request);

      assertThat(wrapped).isNotSameAs(request);
      assertThat(wrapped.getHeader("X-Forwarded-For")).isEqualTo("10.0.0.5");
      assertThat(wrapped.getHeader("X-Forwarded-Proto")).isEqualTo("https");
      assertThat(wrapped.getHeader("X-Forwarded-Host")).isEqualTo("gateway.internal");
      assertThat(wrapped.getHeader("Forwarded"))
          .isEqualTo("for=10.0.0.5;proto=https;host=gateway.internal");
    }

    @Test
    @DisplayName("preserves existing header values rather than overwriting them")
    void preservesExistingValues() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.setRemoteAddr("10.0.0.5");
      request.addHeader("X-Forwarded-For", "203.0.113.9");

      HttpServletRequest wrapped = ForwardedHeaderRequestWrapper.wrap(request);

      assertThat(wrapped.getHeader("X-Forwarded-For")).isEqualTo("203.0.113.9");
      // Other headers still get synthesized
      assertThat(wrapped.getHeader("X-Forwarded-Proto")).isNotNull();
    }

    @Test
    @DisplayName("synthesizes only the missing subset when some headers are partially present")
    void synthesizesPartialSubset() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Forwarded-For", "203.0.113.9");
      request.addHeader("X-Forwarded-Proto", "https");
      // Host and Forwarded left absent

      HttpServletRequest wrapped = ForwardedHeaderRequestWrapper.wrap(request);

      assertThat(wrapped.getHeader("X-Forwarded-For")).isEqualTo("203.0.113.9");
      assertThat(wrapped.getHeader("X-Forwarded-Proto")).isEqualTo("https");
      assertThat(wrapped.getHeader("X-Forwarded-Host")).isNotNull();
      assertThat(wrapped.getHeader("Forwarded")).isNotNull();
    }
  }

  @Nested
  @DisplayName("wrapped request accessors")
  class WrappedAccessors {

    @Test
    @DisplayName("getHeaders returns single-element enumeration for synthesized header")
    void getHeadersReturnsSyntheticValue() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      HttpServletRequest wrapped = ForwardedHeaderRequestWrapper.wrap(request);

      List<String> values = Collections.list(wrapped.getHeaders("X-Forwarded-For"));
      assertThat(values).hasSize(1);
    }

    @Test
    @DisplayName("getHeaders returns empty enumeration for a header that was never synthesized")
    void getHeadersReturnsEmptyWhenAbsent() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      HttpServletRequest wrapped = ForwardedHeaderRequestWrapper.wrap(request);

      List<String> values = Collections.list(wrapped.getHeaders("X-Nonexistent-Header"));
      assertThat(values).isEmpty();
    }

    @Test
    @DisplayName("getHeaderNames includes synthesized header names alongside originals")
    void getHeaderNamesIncludesSynthesized() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
      request.addHeader("X-Custom", "value");

      HttpServletRequest wrapped = ForwardedHeaderRequestWrapper.wrap(request);

      List<String> names = Collections.list(wrapped.getHeaderNames());
      assertThat(names).contains("X-Custom", "X-Forwarded-For", "X-Forwarded-Proto");
    }
  }
}
