package com.example.commonlib.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private ObjectMapper objectMapper;
  @Mock private ProceedingJoinPoint joinPoint;
  @Mock private MethodSignature methodSignature;
  @Mock private HttpServletRequest request;
  @Mock private Idempotent idempotent;

  @InjectMocks private IdempotencyAspect aspect;

  @BeforeEach
  void setUp() {
    // Use lenient() to avoid unnecessary stubbing errors when the stub is not used in some tests
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Mock request context
    ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
    when(attributes.getRequest()).thenReturn(request);
    RequestContextHolder.setRequestAttributes(attributes);
  }

  @Test
  void handle_missingIdempotencyKey_throwsException() throws Throwable {
    when(request.getHeader("Idempotency-Key")).thenReturn(null);

    assertThatThrownBy(() -> aspect.handle(joinPoint, idempotent))
        .isInstanceOf(Exception.class)
        .hasMessage("Idempotency-Key header is required");

    verify(redisTemplate, never()).opsForValue();
    verifyNoInteractions(valueOperations);
  }

  @Test
  void handle_firstRequest_acquiresLockAndProceeds() throws Throwable {
    String key = "idempotency:/api/test:abc-123";
    when(request.getHeader("Idempotency-Key")).thenReturn("abc-123");
    when(request.getRequestURI()).thenReturn("/api/test");
    when(idempotent.ttlSeconds()).thenReturn(60L);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(true);
    when(joinPoint.proceed()).thenReturn("result");
    when(objectMapper.writeValueAsString("result")).thenReturn("{\"data\":\"result\"}");

    aspect.handle(joinPoint, idempotent);

    verify(valueOperations).setIfAbsent(eq(key), eq("PROCESSING"), any(Duration.class));
    verify(joinPoint).proceed();
    verify(valueOperations).set(eq(key), eq("{\"data\":\"result\"}"), any(Duration.class));
  }

  @Test
  void handle_duplicateInFlight_throwsException() throws Throwable {
    String key = "idempotency:/api/test:abc-123";
    when(request.getHeader("Idempotency-Key")).thenReturn("abc-123");
    when(request.getRequestURI()).thenReturn("/api/test");
    when(idempotent.ttlSeconds()).thenReturn(60L);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(false);
    when(valueOperations.get(key)).thenReturn("PROCESSING");

    assertThatThrownBy(() -> aspect.handle(joinPoint, idempotent))
        .isInstanceOf(Exception.class)
        .hasMessage("Request already being processed");

    verify(joinPoint, never()).proceed();
    verify(valueOperations).get(key);
  }

  @Test
  void handle_duplicateCompleted_returnsCachedResult() throws Throwable {
    String key = "idempotency:/api/test:abc-123";
    String cachedJson = "{\"data\":\"cached\"}";
    when(request.getHeader("Idempotency-Key")).thenReturn("abc-123");
    when(request.getRequestURI()).thenReturn("/api/test");
    when(idempotent.ttlSeconds()).thenReturn(60L);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(false);
    when(valueOperations.get(key)).thenReturn(cachedJson);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getReturnType()).thenReturn(String.class);
    when(objectMapper.readValue(cachedJson, String.class)).thenReturn("cached result");

    Object result = aspect.handle(joinPoint, idempotent);
    assertThat(result).isEqualTo("cached result");

    verify(joinPoint, never()).proceed();
    verify(valueOperations).get(key);
    verify(objectMapper).readValue(cachedJson, String.class);
  }

  @Test
  void handle_exceptionInProceed_deletesKeyAndThrows() throws Throwable {
    String key = "idempotency:/api/test:abc-123";
    when(request.getHeader("Idempotency-Key")).thenReturn("abc-123");
    when(request.getRequestURI()).thenReturn("/api/test");
    when(idempotent.ttlSeconds()).thenReturn(60L);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(true);
    when(joinPoint.proceed()).thenThrow(new RuntimeException("service error"));

    assertThatThrownBy(() -> aspect.handle(joinPoint, idempotent))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("service error");

    verify(redisTemplate).delete(key);
  }

  @Test
  void handle_emptyIdempotencyKey_throwsException() throws Throwable {
    when(request.getHeader("Idempotency-Key")).thenReturn("   "); // only whitespace

    assertThatThrownBy(() -> aspect.handle(joinPoint, idempotent))
        .isInstanceOf(Exception.class)
        .hasMessage("Idempotency-Key header is required");

    verify(redisTemplate, never()).opsForValue();
  }
}
