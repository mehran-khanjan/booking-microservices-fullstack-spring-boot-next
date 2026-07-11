package com.example.communicationservice.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Prevents duplicate side effects (e.g., double emails or SMS) when RabbitMQ redelivers a message
 * (at‑least‑once delivery is normal after a consumer crash before acknowledgment).
 *
 * <p>Uses Redis {@code SETNX} for an atomic "claim this eventId" check. Once a key is set (with a
 * TTL of {@value #DEDUPE_WINDOW}), the same eventId cannot be claimed again within that window.
 *
 * <p>The key format is {@code notif:processed:<eventId>}.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

  private final StringRedisTemplate redisTemplate;
  private static final Duration DEDUPE_WINDOW = Duration.ofHours(24);

  /**
   * Attempts to claim the given event ID for processing.
   *
   * @param eventId the unique identifier of the event
   * @return {@code true} if this eventId was NOT seen before (i.e., it is safe to process now),
   *     {@code false} if the event has already been claimed (or is still in the deduplication
   *     window)
   */
  public boolean claim(String eventId) {
    Boolean firstTime =
        redisTemplate.opsForValue().setIfAbsent("notif:processed:" + eventId, "1", DEDUPE_WINDOW);
    return Boolean.TRUE.equals(firstTime);
  }
}
