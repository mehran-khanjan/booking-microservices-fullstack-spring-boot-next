package com.example.bookingservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} methods (see {@code BookingHoldExpiryScheduler}). Without this,
 * {@code @Scheduled} annotations are silently ignored and the hold-expiry sweep never runs.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
