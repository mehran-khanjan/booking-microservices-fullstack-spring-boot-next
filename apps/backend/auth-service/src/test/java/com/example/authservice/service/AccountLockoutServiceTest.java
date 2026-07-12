package com.example.authservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commonlib.exception.AccountLockedException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountLockoutServiceTest {

  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private AccountLockoutService service;

  @BeforeEach
  void setUp() {
    service = new AccountLockoutService(redisTemplate);
    ReflectionTestUtils.setField(service, "maxAttempts", 5);
    ReflectionTestUtils.setField(service, "lockoutDurationMinutes", 30L);
  }

  @Test
  void recordFailedLogin_belowThreshold_doesNotLock() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("login:attempt:user@example.com")).thenReturn(2L);

    service.recordFailedLogin("user@example.com");

    verify(redisTemplate)
        .expire(eq("login:attempt:user@example.com"), eq(30L), eq(TimeUnit.MINUTES));
    verify(valueOperations, never()).set(eq("lockout:user@example.com"), any(), anyLong(), any());
  }

  @Test
  void recordFailedLogin_atThreshold_locksAccount_andThrows() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("login:attempt:user@example.com")).thenReturn(5L);

    assertThatThrownBy(() -> service.recordFailedLogin("user@example.com"))
        .isInstanceOf(AccountLockedException.class);

    verify(valueOperations).set("lockout:user@example.com", "locked", 30L, TimeUnit.MINUTES);
    verify(redisTemplate).delete("login:attempt:user@example.com");
  }

  @Test
  void recordFailedLogin_blankIdentifier_throwsIllegalArgument() {
    assertThatThrownBy(() -> service.recordFailedLogin(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void checkAccountLocked_nullIdentifier_isNoOp() {
    service.checkAccountLocked(null);

    verifyNoRedisReads();
  }

  @Test
  void checkAccountLocked_locked_throwsAccountLockedException() {
    when(redisTemplate.hasKey("lockout:user@example.com")).thenReturn(true);
    when(redisTemplate.getExpire("lockout:user@example.com", TimeUnit.MINUTES)).thenReturn(15L);

    assertThatThrownBy(() -> service.checkAccountLocked("user@example.com"))
        .isInstanceOf(AccountLockedException.class)
        .hasMessageContaining("15");
  }

  @Test
  void checkAccountLocked_notLocked_doesNotThrow() {
    when(redisTemplate.hasKey("lockout:user@example.com")).thenReturn(false);

    service.checkAccountLocked("user@example.com");
  }

  @Test
  void resetFailedAttempts_deletesAttemptKey() {
    service.resetFailedAttempts("user@example.com");

    verify(redisTemplate).delete("login:attempt:user@example.com");
  }

  @Test
  void resetFailedAttempts_nullIdentifier_isNoOp() {
    service.resetFailedAttempts(null);

    verifyNoRedisReads();
  }

  private void verifyNoRedisReads() {
    org.mockito.Mockito.verifyNoInteractions(redisTemplate);
  }
}
