package com.example.flightservice.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration class that provides a dedicated thread pool for asynchronous flight search tasks.
 *
 * <p>The executor is tuned to handle concurrent outbound and return leg searches without
 * overwhelming the database connection pool. It applies back‑pressure via {@code CallerRunsPolicy}
 * and ensures graceful shutdown.
 *
 * @see ThreadPoolTaskExecutor
 * @see ThreadPoolExecutor.CallerRunsPolicy
 */
@Configuration
public class AsyncExecutorConfig {

  /**
   * Creates the {@code flightSearchExecutor} bean.
   *
   * <p>The pool is sized conservatively:
   *
   * <ul>
   *   <li>corePoolSize = 10 (always active threads)
   *   <li>maxPoolSize = 30 (upper bound when queue fills)
   *   <li>queueCapacity = 200 (backlog before scaling to max)
   * </ul>
   *
   * <p>A {@code CallerRunsPolicy} is used to prevent silent task dropping – when the queue is full
   * and max threads are busy, the calling thread executes the task, providing natural
   * back‑pressure.
   *
   * <p>Shutdown waits up to 20 seconds for in‑flight tasks to complete.
   *
   * @return the configured {@link Executor} instance
   */
  @Bean(name = "flightSearchExecutor")
  public Executor flightSearchExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(30);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("flight-search-");
    // Back-pressure instead of silently dropping tasks
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(20);
    executor.initialize();
    return executor;
  }
}
