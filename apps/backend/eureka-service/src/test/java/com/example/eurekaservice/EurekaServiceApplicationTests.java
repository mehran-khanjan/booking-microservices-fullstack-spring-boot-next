package com.example.eurekaservice;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {"eureka.client.register-with-eureka=false", "eureka.client.fetch-registry=false"})
class EurekaServiceApplicationTests {

  @Test
  void contextLoads() {
    // Verifies that the application context starts successfully
  }

  @Test
  void mainMethodRuns() {
    assertThatCode(() -> EurekaServiceApplication.main(new String[] {})).doesNotThrowAnyException();
  }
}
