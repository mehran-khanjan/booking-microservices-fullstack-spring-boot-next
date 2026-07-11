package com.example.authservice;

import com.example.authservice.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AuthServiceApplicationTests {

  @Test
  void contextLoads() {
    // Verifies that the application context starts correctly
  }
}
