package com.example.authservice.property;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration properties for CORS allowed origins (prefix: {@code app.cors}). */
@Configuration
@ConfigurationProperties(prefix = "app.cors")
@Getter
@Setter
public class CorsProperties {
  private List<String> allowedOrigins;
}
