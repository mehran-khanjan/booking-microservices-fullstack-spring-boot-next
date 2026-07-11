package com.example.commonlib.responseenvelope.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class RequestContextTest {

  private MockedStatic<RequestContextHolder> requestContextHolderMock;
  private MockedStatic<MDC> mdcMock;
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    requestContextHolderMock = mockStatic(RequestContextHolder.class);
    mdcMock = mockStatic(MDC.class);
    request = mock(HttpServletRequest.class);
  }

  @AfterEach
  void tearDown() {
    requestContextHolderMock.close();
    mdcMock.close();
  }

  @Test
  void getTraceId_fromMDC_returnsValue() {
    mdcMock.when(() -> MDC.get("requestId")).thenReturn("mdc-trace");
    assertThat(RequestContext.getTraceId()).isEqualTo("mdc-trace");
  }

  @Test
  void getTraceId_fromRequestAttribute_whenMdcNull() {
    mdcMock.when(() -> MDC.get("requestId")).thenReturn(null);
    ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
    when(attributes.getRequest()).thenReturn(request);
    requestContextHolderMock
        .when(RequestContextHolder::getRequestAttributes)
        .thenReturn(attributes);
    when(request.getAttribute("requestId")).thenReturn("attr-trace");

    assertThat(RequestContext.getTraceId()).isEqualTo("attr-trace");
  }

  @Test
  void getTraceId_fromHeaderXRequestId_whenAttributeNull() {
    mdcMock.when(() -> MDC.get("requestId")).thenReturn(null);
    ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
    when(attributes.getRequest()).thenReturn(request);
    requestContextHolderMock
        .when(RequestContextHolder::getRequestAttributes)
        .thenReturn(attributes);
    when(request.getAttribute("requestId")).thenReturn(null);
    when(request.getHeader("X-Request-ID")).thenReturn("header-request");

    assertThat(RequestContext.getTraceId()).isEqualTo("header-request");
  }

  @Test
  void getTraceId_fromHeaderXCorrelationId_whenXRequestIdNull() {
    mdcMock.when(() -> MDC.get("requestId")).thenReturn(null);
    ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
    when(attributes.getRequest()).thenReturn(request);
    requestContextHolderMock
        .when(RequestContextHolder::getRequestAttributes)
        .thenReturn(attributes);
    when(request.getAttribute("requestId")).thenReturn(null);
    when(request.getHeader("X-Request-ID")).thenReturn(null);
    when(request.getHeader("X-Correlation-ID")).thenReturn("correlation");

    assertThat(RequestContext.getTraceId()).isEqualTo("correlation");
  }

  @Test
  void getTraceId_fallbackToUnknown() {
    mdcMock.when(() -> MDC.get("requestId")).thenReturn(null);
    ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
    when(attributes.getRequest()).thenReturn(request);
    requestContextHolderMock
        .when(RequestContextHolder::getRequestAttributes)
        .thenReturn(attributes);
    when(request.getAttribute("requestId")).thenReturn(null);
    when(request.getHeader("X-Request-ID")).thenReturn(null);
    when(request.getHeader("X-Correlation-ID")).thenReturn(null);

    assertThat(RequestContext.getTraceId()).isEqualTo("UNKNOWN");
  }

  @Test
  void getRequestPath_returnsUri() {
    ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
    when(attributes.getRequest()).thenReturn(request);
    requestContextHolderMock
        .when(RequestContextHolder::getRequestAttributes)
        .thenReturn(attributes);
    when(request.getRequestURI()).thenReturn("/api/test");

    assertThat(RequestContext.getRequestPath()).isEqualTo("/api/test");
  }

  @Test
  void getRequestPath_fallbackUnknown() {
    requestContextHolderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(null);
    assertThat(RequestContext.getRequestPath()).isEqualTo("/unknown");
  }

  @Test
  void getCurrentRequest_returnsOptionalEmptyWhenNoAttributes() {
    requestContextHolderMock.when(RequestContextHolder::getRequestAttributes).thenReturn(null);
    assertThat(RequestContext.getCurrentRequest()).isEmpty();
  }

  @Test
  void getCurrentRequest_returnsRequestWhenAttributesPresent() {
    ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
    when(attributes.getRequest()).thenReturn(request);
    requestContextHolderMock
        .when(RequestContextHolder::getRequestAttributes)
        .thenReturn(attributes);
    assertThat(RequestContext.getCurrentRequest()).contains(request);
  }
}
