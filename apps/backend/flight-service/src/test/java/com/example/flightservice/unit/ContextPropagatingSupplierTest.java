package com.example.flightservice.unit;

import com.example.flightservice.util.ContextPropagatingSupplier;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ContextPropagatingSupplierTest {

  @Test
  void shouldPropagateMDCToSupplier() {
    MDC.put("traceId", "abc-123");

    Supplier<String> wrapped = ContextPropagatingSupplier.wrap(() -> MDC.get("traceId"));

    String result = wrapped.get();
    assertThat(result).isEqualTo("abc-123");
    MDC.clear();
  }

  @Test
  void shouldPropagateSecurityContextToSupplier() {
    SecurityContext ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(mock(Authentication.class));
    SecurityContextHolder.setContext(ctx);

    AtomicReference<SecurityContext> captured = new AtomicReference<>();
    Supplier<Void> wrapped = ContextPropagatingSupplier.wrap(() -> {
      captured.set(SecurityContextHolder.getContext());
      return null;
    });

    wrapped.get();
    assertThat(captured.get()).isSameAs(ctx);
  }

  @Test
  void shouldRestorePreviousMDCAfterExecution() {
    MDC.put("original", "value1");

    Supplier<String> wrapped = ContextPropagatingSupplier.wrap(() -> {
      MDC.put("inside", "modified");
      return "done";
    });

    wrapped.get();

    assertThat(MDC.get("original")).isEqualTo("value1");
    assertThat(MDC.get("inside")).isNull();
    MDC.clear();
  }

  @Test
  void shouldRestorePreviousSecurityContextAfterExecution() {
    SecurityContext original = SecurityContextHolder.createEmptyContext();
    original.setAuthentication(mock(Authentication.class));
    SecurityContextHolder.setContext(original);

    Supplier<Void> wrapped = ContextPropagatingSupplier.wrap(() -> {
      SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
      return null;
    });

    wrapped.get();
    assertThat(SecurityContextHolder.getContext()).isSameAs(original);
  }

  @Test
  void shouldHandleNullMDC() {
    MDC.clear();

    Supplier<String> wrapped = ContextPropagatingSupplier.wrap(() -> "result");

    String result = wrapped.get();
    assertThat(result).isEqualTo("result");
  }
}