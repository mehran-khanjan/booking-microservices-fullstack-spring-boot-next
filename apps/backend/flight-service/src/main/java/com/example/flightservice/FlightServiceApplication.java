package com.example.flightservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Main entry point for the Flight Service Spring Boot application.
 *
 * <p>This service provides flight search, availability, and booking capabilities. It is a
 * microservice that registers with the service discovery (e.g., Eureka) and exposes REST APIs for
 * flight operations.
 *
 * <p>The application is configured with:
 *
 * <ul>
 *   <li>Spring Boot auto‑configuration
 *   <li>Service discovery via {@link EnableDiscoveryClient}
 *   <li>OAuth2 resource server security
 *   <li>JPA with PostgreSQL (or H2 for dev)
 *   <li>Resilience4j for rate limiting and circuit breaking
 * </ul>
 *
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 * @see org.springframework.cloud.client.discovery.EnableDiscoveryClient
 */
@SpringBootApplication
@EnableDiscoveryClient
public class FlightServiceApplication {

  /**
   * Launches the Flight Service application.
   *
   * @param args command‑line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(FlightServiceApplication.class, args);
  }
}
