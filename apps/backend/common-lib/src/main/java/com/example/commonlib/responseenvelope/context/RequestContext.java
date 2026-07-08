package com.example.commonlib.responseenvelope.context;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility to extract trace ID and request path from the current HTTP request context.
 *
 * <p>Works with Spring's {@link RequestContextHolder} and MDC.
 */
public final class RequestContext {

  private static final String MDC_REQUEST_ID_KEY = "requestId";

  private RequestContext() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** Get trace ID from MDC (set by filter.TraceIdFilter) */
  public static String getTraceId() {
    // 1. Try MDC
    String requestId = MDC.get(MDC_REQUEST_ID_KEY);
    if (requestId != null) {
      return requestId;
    }

    // 2. Try request attribute (set by a filter, if any)
    Optional<HttpServletRequest> reqOpt = getCurrentRequest();
    if (reqOpt.isPresent()) {
      HttpServletRequest req = reqOpt.get();
      // Check attribute
      String attrId = (String) req.getAttribute(MDC_REQUEST_ID_KEY);
      if (attrId != null) {
        return attrId;
      }

      // 3. Fallback to headers (your gateway forwards X-Request-ID)
      String headerId = req.getHeader("X-Request-ID");
      if (headerId != null && !headerId.isBlank()) {
        return headerId;
      }
      // Also try X-Correlation-ID as alternative
      headerId = req.getHeader("X-Correlation-ID");
      if (headerId != null && !headerId.isBlank()) {
        return headerId;
      }
    }

    return "UNKNOWN";
  }

  /** Get current request path */
  public static String getRequestPath() {
    return getCurrentRequest().map(HttpServletRequest::getRequestURI).orElse("/unknown");
  }

  /** Get current HttpServletRequest */
  public static Optional<HttpServletRequest> getCurrentRequest() {
    try {
      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

      if (attributes != null) {
        return Optional.of(attributes.getRequest());
      }
    } catch (IllegalStateException e) {
      // Not in request context (e.g., async thread)
    }

    return Optional.empty();
  }
}
