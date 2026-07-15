package com.example.bookingservice.unit;

import com.example.bookingservice.servcie.BookingLockingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingLockingServiceTest {

  @Mock private RedissonClient redissonClient;
  @Mock private RLock lock;
  @Mock private RLock lock2;
  @Mock private RLock multiLock;

  private BookingLockingService lockingService;

  @BeforeEach
  void setUp() {
    lockingService = new BookingLockingService(redissonClient);
  }

  @Nested
  class ExecuteWithLock {

    @Test
    void shouldExecuteActionWhenLockAcquired() throws InterruptedException {
      when(redissonClient.getLock("flight:lock:1")).thenReturn(lock);
      when(lock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      String result = lockingService.executeWithLock(
          "flight:lock:1", 10, 30, () -> "done");

      assertThat(result).isEqualTo("done");
      verify(lock).unlock();
    }

    @Test
    void shouldThrowWhenLockNotAcquired() throws InterruptedException {
      when(redissonClient.getLock("flight:lock:1")).thenReturn(lock);
      when(lock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(false);

      assertThatThrownBy(() -> lockingService.executeWithLock(
          "flight:lock:1", 10, 30, (Supplier<String>) () -> "done"))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Could not acquire lock");
    }

    @Test
    void shouldThrowWhenInterrupted() throws InterruptedException {
      when(redissonClient.getLock("flight:lock:1")).thenReturn(lock);
      when(lock.tryLock(10, 30, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupted"));

      assertThatThrownBy(() -> lockingService.executeWithLock(
          "flight:lock:1", 10, 30, (Supplier<String>) () -> "done"))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("interrupted");
    }

    @Test
    void shouldHandleVoidAction() throws InterruptedException {
      when(redissonClient.getLock("flight:lock:1")).thenReturn(lock);
      when(lock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      Runnable action = mock(Runnable.class);
      lockingService.executeWithLock("flight:lock:1", 10, 30, action);

      verify(action).run();
      verify(lock).unlock();
    }
  }

  @Nested
  class ExecuteWithMultiLock {

    @Test
    void shouldExecuteActionWhenMultiLockAcquired() throws InterruptedException {
      List<String> lockKeys = List.of("flight:lock:1", "flight:lock:2");

      when(redissonClient.getLock("flight:lock:1")).thenReturn(lock);
      when(redissonClient.getLock("flight:lock:2")).thenReturn(lock2);
      when(redissonClient.getMultiLock(lock, lock2)).thenReturn(multiLock);
      when(multiLock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(true);

      String result = lockingService.executeWithMultiLock(
          lockKeys, 10, 30, () -> "done");

      assertThat(result).isEqualTo("done");
      verify(multiLock).unlock();
    }

    @Test
    void shouldThrowWhenMultiLockNotAcquired() throws InterruptedException {
      List<String> lockKeys = List.of("flight:lock:1");

      when(redissonClient.getLock("flight:lock:1")).thenReturn(lock);
      when(redissonClient.getMultiLock(lock)).thenReturn(multiLock);
      when(multiLock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(false);

      assertThatThrownBy(() -> lockingService.executeWithMultiLock(
          lockKeys, 10, 30, (Supplier<String>) () -> "done"))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Could not acquire locks");
    }

    @Test
    void shouldThrowWhenLockKeysEmpty() {
      assertThatThrownBy(() -> lockingService.executeWithMultiLock(
          List.of(), 10, 30, (Supplier<String>) () -> "done"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("At least one lock key");
    }

    @Test
    void shouldThrowWhenLockKeysNull() {
      assertThatThrownBy(() -> lockingService.executeWithMultiLock(
          null, 10, 30, (Supplier<String>) () -> "done"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("At least one lock key");
    }

    @Test
    void shouldHandleVoidMultiLockAction() throws InterruptedException {
      List<String> lockKeys = List.of("flight:lock:1");

      when(redissonClient.getLock("flight:lock:1")).thenReturn(lock);
      when(redissonClient.getMultiLock(lock)).thenReturn(multiLock);
      when(multiLock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(true);

      Runnable action = mock(Runnable.class);
      lockingService.executeWithMultiLock(lockKeys, 10, 30, action);

      verify(action).run();
      verify(multiLock).unlock();
    }

    @Test
    void shouldHandleMultiLockUnlockException() throws InterruptedException {
      List<String> lockKeys = List.of("flight:lock:1");

      when(redissonClient.getLock("flight:lock:1")).thenReturn(lock);
      when(redissonClient.getMultiLock(lock)).thenReturn(multiLock);
      when(multiLock.tryLock(10, 30, TimeUnit.SECONDS)).thenReturn(true);
      doThrow(new RuntimeException("unlock failed")).when(multiLock).unlock();

      String result = lockingService.executeWithMultiLock(
          lockKeys, 10, 30, () -> "done");

      assertThat(result).isEqualTo("done");
    }
  }
}