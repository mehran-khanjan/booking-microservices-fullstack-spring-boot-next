package com.example.authservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class that enables Spring's scheduled task execution capability.
 *
 * <p>This class serves solely as a marker to activate the scheduling infrastructure via the {@link
 * EnableScheduling} annotation. By placing this annotation in a {@link Configuration} class, Spring
 * will detect {@code @Scheduled} methods within beans and execute them according to their specified
 * schedules.
 *
 * <p>Actual scheduled tasks (e.g., cron jobs, fixed-rate or fixed-delay tasks) are defined
 * elsewhere in the application using the {@link
 * org.springframework.scheduling.annotation.Scheduled} annotation on methods within any
 * Spring-managed bean. This configuration does not define any tasks itself but enables the
 * processing of such annotations.
 *
 * <p>If no scheduled tasks are present, this configuration is harmless and has no effect.
 *
 * @author Your Team
 * @see org.springframework.scheduling.annotation.Scheduled
 * @see EnableScheduling
 * @since 1.0
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
  // empty, just to enable scheduling
}
