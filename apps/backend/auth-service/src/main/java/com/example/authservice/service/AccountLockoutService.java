package com.example.authservice.service;

import com.example.commonlib.exception.AccountLockedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Service that tracks failed login attempts per identifier (email or phone) and enforces a
 * temporary account lockout after a configurable number of failures.
 *
 * <p>State is stored in Redis, making it shared across all instances of the authentication service.
 * This allows consistent lockout enforcement even in a distributed environment.
 *
 * <p>The service provides three main operations:
 *
 * <ul>
 *   <li>{@link #recordFailedLogin(String)} – increments the attempt counter, locks the account if
 *       the threshold is reached, and throws {@link AccountLockedException}.
 *   <li>{@link #checkAccountLocked(String)} – throws {@link AccountLockedException} if the
 *       identifier is currently locked.
 *   <li>{@link #resetFailedAttempts(String)} – clears the attempt counter for a successful login.
 * </ul>
 *
 * <p>All Redis operations are protected with a circuit breaker and retry. In case of Redis
 * unavailability, the service falls back to a <strong>fail‑open</strong> strategy – allowing login
 * attempts to proceed (except for business exceptions like {@code IllegalArgumentException}) – to
 * avoid locking users out due to infrastructure issues.
 *
 * <p>Configuration:
 *
 * <ul>
 *   <li>{@code account.lockout.max-attempts} – number of failed attempts before lockout (default:
 *       5)
 *   <li>{@code account.lockout.duration-minutes} – lockout duration in minutes (default: 30)
 * </ul>
 *
 * @author Your Team
 * @see AccountLockedException
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountLockoutService {

  /** Redis template for storing attempt counts and lockout flags. */
  private final RedisTemplate<String, String> redisTemplate;

  /** Maximum allowed failed attempts before lockout. */
  @Value("${account.lockout.max-attempts:5}")
  private int maxAttempts;

  /** Duration of the lockout in minutes. */
  @Value("${account.lockout.duration-minutes:30}")
  private long lockoutDurationMinutes;

  /** Redis key prefix for lockout flags. */
  private static final String LOCKOUT_PREFIX = "lockout:";

  /** Redis key prefix for attempt counters. */
  private static final String ATTEMPT_PREFIX = "login:attempt:";

  /**
   * Records a failed login attempt for the given identifier.
   *
   * <p>This method increments the attempt counter in Redis and sets a TTL equal to the lockout
   * duration. If the number of attempts reaches {@code maxAttempts}, the account is locked (a
   * lockout flag is set) and an {@link AccountLockedException} is thrown immediately.
   *
   * <p>The identifier must be the value the user authenticated with (email or phone) and must not
   * be {@code null} or blank.
   *
   * <p>If Redis is unavailable, the fallback fails‑open (the exception is logged but no lockout is
   * enforced), except for {@code IllegalArgumentException} which is rethrown.
   *
   * @param identifier the email address or phone number (must have text)
   * @throws AccountLockedException if the maximum attempts are reached
   * @throws IllegalArgumentException if the identifier is blank
   */
  @CircuitBreaker(name = "redis", fallbackMethod = "recordFailedLoginFallback")
  @Retry(name = "redis")
  public void recordFailedLogin(String identifier) {
    Assert.hasText(identifier, "identifier must not be blank");

    String attemptKey = ATTEMPT_PREFIX + identifier;
    Long attempts = redisTemplate.opsForValue().increment(attemptKey);
    redisTemplate.expire(attemptKey, lockoutDurationMinutes, TimeUnit.MINUTES);

    log.warn("Failed login attempt {}/{} for identifier={}", attempts, maxAttempts, identifier);

    if (attempts != null && attempts >= maxAttempts) {
      redisTemplate
          .opsForValue()
          .set(LOCKOUT_PREFIX + identifier, "locked", lockoutDurationMinutes, TimeUnit.MINUTES);
      redisTemplate.delete(attemptKey);
      log.error("🔒 Account locked for {}m identifier={}", lockoutDurationMinutes, identifier);
      throw new AccountLockedException(
          "Account locked due to "
              + maxAttempts
              + " failed attempts. Try again in "
              + lockoutDurationMinutes
              + " minutes.");
    }
  }

  /**
   * Fallback for {@link #recordFailedLogin(String)} when Redis is unavailable.
   *
   * @param identifier the identifier
   * @param t the exception that caused the fallback
   * @throws IllegalArgumentException if the original exception was of that type
   */
  private void recordFailedLoginFallback(String identifier, Throwable t) {
    log.error("event=lockout_circuit_open op=recordFailedLogin identifier={}", identifier, t);

    // Rethrow client‑side validation errors, otherwise fail‑open (no lockout enforced)
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    // For any other failure (Redis down), we simply don't record the attempt → fail‑open.
  }

  /**
   * Checks whether the given identifier is currently locked.
   *
   * <p>If the identifier is {@code null}, this method does nothing (no check performed). Otherwise,
   * it looks up the lockout flag in Redis. If present, it throws an {@link AccountLockedException}
   * with the remaining lockout time.
   *
   * <p>In case of Redis failure, the fallback fails‑open (allows login attempts) except for {@code
   * IllegalArgumentException} which is rethrown.
   *
   * @param identifier the email or phone (may be {@code null})
   * @throws AccountLockedException if the account is locked
   */
  @CircuitBreaker(name = "redis", fallbackMethod = "checkAccountLockedFallback")
  @Retry(name = "redis")
  public void checkAccountLocked(String identifier) {
    if (identifier == null) return; // nothing to check yet (e.g. pre-resolution)

    String lockoutKey = LOCKOUT_PREFIX + identifier;
    if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
      Long ttl = redisTemplate.getExpire(lockoutKey, TimeUnit.MINUTES);
      log.warn("🔒 Locked account attempted login: identifier={}", identifier);
      throw new AccountLockedException("Account is locked. Try again in " + ttl + " minutes.");
    }
  }

  /**
   * Fallback for {@link #checkAccountLocked(String)} when Redis is unavailable.
   *
   * @param identifier the identifier
   * @param t the exception
   * @throws IllegalArgumentException if the original exception was of that type
   */
  private void checkAccountLockedFallback(String identifier, Throwable t) {
    log.error("event=lockout_circuit_open op=checkAccountLocked identifier={}", identifier, t);

    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    // Fail‑open: if Redis is unavailable, we allow the login attempt.
  }

  /**
   * Resets the failed attempt counter for the given identifier.
   *
   * <p>This should be called after a successful login to clear the attempt history. If the
   * identifier is {@code null}, the method does nothing.
   *
   * @param identifier the email or phone (may be {@code null})
   */
  public void resetFailedAttempts(String identifier) {
    if (identifier == null) return;
    redisTemplate.delete(ATTEMPT_PREFIX + identifier);
    log.debug("Reset failed login attempts for identifier={}", identifier);
  }
}
