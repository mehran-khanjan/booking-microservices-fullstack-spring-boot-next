package com.example.flightservice.util;

import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for propagating diagnostic context and security context when executing a {@link
 * Supplier} in a different thread.
 *
 * <p>This is essential for async processing where the thread pool does not automatically inherit
 * MDC (Mapped Diagnostic Context) or the Spring Security {@link SecurityContext}. By wrapping a
 * supplier with {@link #wrap(Supplier)}, the calling thread's MDC and SecurityContext are captured
 * and restored inside the supplier's execution.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * Supplier<Flight> wrapped = ContextPropagatingSupplier.wrap(() -> flightService.findById(id));
 * CompletableFuture.supplyAsync(wrapped, executor);
 * }</pre>
 *
 * @see MDC
 * @see SecurityContextHolder
 */
public final class ContextPropagatingSupplier {

  private ContextPropagatingSupplier() {
    // Prevent instantiation
  }

  /**
   * Wraps a supplier so that MDC (log correlation id, trace id, etc.) and the SecurityContext of
   * the calling thread are propagated to whatever thread executes the supplier.
   *
   * <p>The method captures the current MDC map and SecurityContext at the time of invocation, and
   * restores them inside the wrapped supplier, also restoring the previous context after the
   * supplier completes.
   *
   * @param delegate the original supplier to wrap
   * @param <T> the type of the supplier's result
   * @return a new supplier that propagates MDC and SecurityContext
   */
  public static <T> Supplier<T> wrap(Supplier<T> delegate) {
    Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
    SecurityContext capturedSecurityContext = SecurityContextHolder.getContext();

    return () -> {
      Map<String, String> previousMdc = MDC.getCopyOfContextMap();
      SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
      try {
        if (capturedMdc != null) {
          MDC.setContextMap(capturedMdc);
        } else {
          MDC.clear();
        }
        SecurityContextHolder.setContext(capturedSecurityContext);
        return delegate.get();
      } finally {
        if (previousMdc != null) {
          MDC.setContextMap(previousMdc);
        } else {
          MDC.clear();
        }
        SecurityContextHolder.setContext(previousSecurityContext);
      }
    };
  }
}
