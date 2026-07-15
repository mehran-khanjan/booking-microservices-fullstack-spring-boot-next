package com.example.bookingservice;

import com.example.bookingservice.config.GrpcClientConfig;
import com.example.bookingservice.config.LockingStrategyConfig;
import com.example.bookingservice.config.SchedulingConfig;
import com.example.bookingservice.config.SecurityConfig;
import com.example.bookingservice.config.TestConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.example.bookingservice",
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "com\\.example\\.bookingservice\\.config\\.RedisConfig")
    })
@Import(TestConfig.class)
public class TestBookingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(TestBookingServiceApplication.class, args);
  }
}