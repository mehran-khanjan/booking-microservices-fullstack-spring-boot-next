package com.example.bookingservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the booking locking strategy. Determines whether to use distributed (Redis) or
 * local pessimistic locking.
 */
@Configuration
@ConfigurationProperties(prefix = "booking.locking")
@Data
public class LockingStrategyConfig {
  /** Locking strategy: "DISTRIBUTED" (default, Redis) or "PESSIMISTIC" (database lock). */
  private String strategy = "DISTRIBUTED";
}
