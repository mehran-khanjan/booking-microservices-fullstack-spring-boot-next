package com.example.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point for the Auth microservice, which handles user authentication and registration. */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.example.authservice.feign")
@EnableScheduling
@ComponentScan(basePackages = {"com.example.outboxlib.outbox", "com.example.authservice"})
@EnableJpaRepositories(basePackages = {"com.example.outboxlib.outbox.repository"})
@EntityScan(basePackages = {"com.example.outboxlib.outbox.entity"})
public class AuthServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
