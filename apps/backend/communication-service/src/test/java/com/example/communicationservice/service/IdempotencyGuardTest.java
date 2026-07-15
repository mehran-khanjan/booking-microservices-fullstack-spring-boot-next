package com.example.communicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class IdempotencyGuardTest {

  @Test
  void claim_firstTimeSeen_returnsTrue() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(eq("notif:processed:evt-1"), eq("1"), any(Duration.class)))
        .thenReturn(true);
    IdempotencyGuard guard = new IdempotencyGuard(redisTemplate);

    boolean result = guard.claim("evt-1");

    assertThat(result).isTrue();
    verify(valueOps).setIfAbsent(eq("notif:processed:evt-1"), eq("1"), eq(Duration.ofHours(24)));
  }

  @Test
  void claim_alreadySeen_returnsFalse() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(eq("notif:processed:evt-2"), eq("1"), any(Duration.class)))
        .thenReturn(false);
    IdempotencyGuard guard = new IdempotencyGuard(redisTemplate);

    boolean result = guard.claim("evt-2");

    assertThat(result).isFalse();
  }

  @Test
  void claim_nullResultFromRedis_treatedAsNotFirstTime() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(eq("notif:processed:evt-3"), eq("1"), any(Duration.class)))
        .thenReturn(null);
    IdempotencyGuard guard = new IdempotencyGuard(redisTemplate);

    boolean result = guard.claim("evt-3");

    assertThat(result).isFalse();
  }
}
