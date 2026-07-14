package com.example.bookingservice.servcie;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * Service for coordinating distributed locks using Redisson. Supports both single‑lock and
 * multi‑lock acquisitions to prevent concurrent modifications of shared resources (e.g., flight
 * seat inventories).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingLockingService {

  private final RedissonClient redissonClient;

  /**
   * Executes an action while holding a single distributed lock.
   *
   * @param lockKey the Redis key for the lock
   * @param waitTime maximum time to wait for the lock (seconds)
   * @param leaseTime time after which the lock is automatically released (seconds)
   * @param action the action to perform under the lock
   * @param <T> the return type of the action
   * @return the result of the action
   * @throws RuntimeException if the lock cannot be acquired or acquisition is interrupted
   */
  public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> action) {
    RLock lock = redissonClient.getLock(lockKey);

    try {
      boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

      if (!acquired) {
        throw new RuntimeException("Could not acquire lock: " + lockKey);
      }

      log.debug("Lock acquired: {}", lockKey);
      return action.get();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Lock acquisition interrupted", e);
    } finally {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
        log.debug("Lock released: {}", lockKey);
      }
    }
  }

  /**
   * Executes a void action while holding a single distributed lock.
   *
   * @param lockKey the Redis key for the lock
   * @param waitTime maximum time to wait for the lock (seconds)
   * @param leaseTime time after which the lock is automatically released (seconds)
   * @param action the action to perform under the lock
   */
  public void executeWithLock(String lockKey, long waitTime, long leaseTime, Runnable action) {
    executeWithLock(
        lockKey,
        waitTime,
        leaseTime,
        () -> {
          action.run();
          return null;
        });
  }

  /**
   * Executes an action while holding locks on ALL the given keys simultaneously using Redisson's
   * {@code RedissonMultiLock}.
   *
   * <p>BUG FIX (#7): previously a single composite key was used, which failed to enforce contention
   * on overlapping flight sets. Now each distinct flight is locked individually, and the caller is
   * responsible for passing keys already de‑duplicated and sorted to avoid deadlocks.
   *
   * @param lockKeys list of Redis keys to lock (must be non‑empty)
   * @param waitTime maximum time to wait for the locks (seconds)
   * @param leaseTime time after which all locks are automatically released (seconds)
   * @param action the action to perform under the multi‑lock
   * @param <T> the return type of the action
   * @return the result of the action
   * @throws IllegalArgumentException if the lockKeys list is null or empty
   * @throws RuntimeException if the locks cannot be acquired or acquisition is interrupted
   */
  public <T> T executeWithMultiLock(
      List<String> lockKeys, long waitTime, long leaseTime, Supplier<T> action) {

    if (lockKeys == null || lockKeys.isEmpty()) {
      throw new IllegalArgumentException("At least one lock key is required");
    }

    RLock[] locks = lockKeys.stream().map(redissonClient::getLock).toArray(RLock[]::new);
    RLock multiLock = redissonClient.getMultiLock(locks);

    try {
      boolean acquired = multiLock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

      if (!acquired) {
        throw new RuntimeException("Could not acquire locks: " + lockKeys);
      }

      log.debug("Multi-lock acquired: {}", lockKeys);
      return action.get();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Lock acquisition interrupted", e);
    } finally {
      try {
        multiLock.unlock();
        log.debug("Multi-lock released: {}", lockKeys);
      } catch (Exception e) {
        log.debug("Multi-lock release skipped/failed for {}: {}", lockKeys, e.getMessage());
      }
    }
  }

  /**
   * Executes a void action while holding locks on all the given keys.
   *
   * @param lockKeys list of Redis keys to lock (must be non‑empty)
   * @param waitTime maximum time to wait for the locks (seconds)
   * @param leaseTime time after which all locks are automatically released (seconds)
   * @param action the action to perform under the multi‑lock
   */
  public void executeWithMultiLock(
      List<String> lockKeys, long waitTime, long leaseTime, Runnable action) {
    executeWithMultiLock(
        lockKeys,
        waitTime,
        leaseTime,
        () -> {
          action.run();
          return null;
        });
  }
}
