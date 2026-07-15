package com.example.bookingservice.config;

import com.example.commonlib.enums.ROLES;
import com.example.flightservice.grpc.FlightServiceGrpc;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestConfig {

  @Bean
  @Primary
  public RedissonClient redissonClient() {
    return mock(RedissonClient.class);
  }

  @Bean
  @Primary
  public FlightServiceGrpc.FlightServiceBlockingStub flightServiceBlockingStub() {
    return mock(FlightServiceGrpc.FlightServiceBlockingStub.class);
  }

  @Bean
  @Primary
  public JwtDecoder jwtDecoder() {
    return mock(JwtDecoder.class);
  }

  @Bean
  public Role role() {
    return new Role();
  }

  public static class Role {
    public String user() {
      return ROLES.USER.getKeycloakRoleName();
    }

    public String admin() {
      return ROLES.ADMIN.getKeycloakRoleName();
    }
  }
}