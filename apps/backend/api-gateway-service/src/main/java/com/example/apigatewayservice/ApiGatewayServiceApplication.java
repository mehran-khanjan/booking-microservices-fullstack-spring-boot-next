package com.example.apigatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for the API Gateway microservice.
 *
 * <p>This service acts as the edge server, routing requests to downstream services with load
 * balancing, and applying common cross‑cutting concerns (tracing, locale, idempotency, forwarded
 * headers).
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayServiceApplication.class, args);
  }
}
