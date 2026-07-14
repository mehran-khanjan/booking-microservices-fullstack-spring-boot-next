package com.example.bookingservice.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis configuration for distributed locking and caching using Redisson. Configures a single Redis
 * server connection with connection pooling.
 */
@Configuration
public class RedisConfig {

  @Value("${spring.redis.host:localhost}")
  private String redisHost;

  @Value("${spring.redis.port:6379}")
  private int redisPort;

  @Value("${spring.redis.database:0}")
  private int database;

  /**
   * Creates and configures a Redisson client for Redis operations.
   *
   * @return the RedissonClient instance
   */
  @Bean
  public RedissonClient redissonClient() {
    Config config = new Config();
    config
        .useSingleServer()
        .setAddress("redis://" + redisHost + ":" + redisPort)
        .setDatabase(database)
        .setConnectionPoolSize(10)
        .setConnectionMinimumIdleSize(5);

    return Redisson.create(config);
  }
}
