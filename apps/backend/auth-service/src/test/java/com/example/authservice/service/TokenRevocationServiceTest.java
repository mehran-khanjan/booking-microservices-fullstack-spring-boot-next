package com.example.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class TokenRevocationServiceTest {

  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private TokenRevocationService service;

  @BeforeEach
  void setUp() {
    service = new TokenRevocationService(redisTemplate);
    ReflectionTestUtils.setField(service, "refreshTokenExpiry", 86400L);
  }

  @Test
  void revokeToken_shortToken_usesTokenAsIs_asKeySuffix() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    service.revokeToken("short-token");

    verify(valueOperations).set("revoked:token:short-token", "revoked", 86400L, TimeUnit.SECONDS);
  }

  @Test
  void revokeToken_longToken_truncatesTo32Chars() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String longToken = "a".repeat(64);

    service.revokeToken(longToken);

    verify(valueOperations)
        .set("revoked:token:" + "a".repeat(32), "revoked", 86400L, TimeUnit.SECONDS);
  }

  @Test
  void isTokenRevoked_whenKeyPresent_returnsTrue() {
    when(redisTemplate.hasKey("revoked:token:short-token")).thenReturn(true);

    assertThat(service.isTokenRevoked("short-token")).isTrue();
  }

  @Test
  void isTokenRevoked_whenKeyAbsent_returnsFalse() {
    when(redisTemplate.hasKey("revoked:token:short-token")).thenReturn(false);

    assertThat(service.isTokenRevoked("short-token")).isFalse();
  }

  @Test
  void isTokenRevoked_whenRedisReturnsNull_returnsFalse() {
    when(redisTemplate.hasKey("revoked:token:short-token")).thenReturn(null);

    assertThat(service.isTokenRevoked("short-token")).isFalse();
  }
}
