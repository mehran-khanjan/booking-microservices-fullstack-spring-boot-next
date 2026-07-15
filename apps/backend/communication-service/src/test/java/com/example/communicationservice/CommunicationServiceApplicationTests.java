package com.example.communicationservice;

import com.example.communicationservice.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class CommunicationServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the full application context (JPA, Flyway, RabbitMQ config,
        // Security filter chain, resilience4j) wires up correctly.
    }

}
