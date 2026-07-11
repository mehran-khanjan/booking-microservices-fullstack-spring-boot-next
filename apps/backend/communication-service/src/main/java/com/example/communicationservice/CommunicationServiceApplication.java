package com.example.communicationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entry point for the Communication Service microservice.
 *
 * <p>This service is responsible for handling communication-related operations, leveraging the
 * outbox pattern for reliable message delivery. It integrates with the outbox library to process
 * inbox messages and manage transactional outbox records.
 *
 * <p>The application is configured with:
 *
 * <ul>
 *   <li>{@link SpringBootApplication} - enables auto-configuration, component scanning, and
 *       additional configuration properties support.
 *   <li>{@link EnableDiscoveryClient} - registers this service with the service discovery (e.g.,
 *       Netflix Eureka) for client-side load balancing and service lookup.
 *   <li>{@link ComponentScan} - scans for Spring-managed components in the outbox library's inbox
 *       package as well as the base package of this service.
 *   <li>{@link EnableJpaRepositories} - enables JPA repositories for the outbox inbox repository,
 *       allowing data access operations on inbox entities.
 *   <li>{@link EntityScan} - scans for JPA entities in the outbox inbox entity package to ensure
 *       they are recognized by the persistence context.
 * </ul>
 *
 * @see com.example.outboxlib.inbox.repository.InboxRepository
 * @see org.springframework.cloud.client.discovery.EnableDiscoveryClient
 * @author [Your Name / Team]
 * @since 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.example.outboxlib.inbox", "com.example.communicationservice"})
@EnableJpaRepositories(basePackages = {"com.example.outboxlib.inbox.repository"})
@EntityScan(basePackages = {"com.example.outboxlib.inbox.entity"})
public class CommunicationServiceApplication {

  /**
   * The main entry point for the Spring Boot application.
   *
   * <p>This method delegates to {@link SpringApplication#run(Class, String...)} to bootstrap the
   * application context, start the embedded web server, and initialize all configured beans and
   * services.
   *
   * @param args command-line arguments passed to the application (e.g., --server.port=8080)
   */
  public static void main(String[] args) {
    SpringApplication.run(CommunicationServiceApplication.class, args);
  }
}
