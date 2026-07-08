package com.example.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/** Entry point for the Auth microservice, which handles user authentication and registration. */
@SpringBootApplication
@EnableDiscoveryClient
// @EnableFeignClients(basePackages = "com.example.booking.auth.feign")
public class AuthServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
