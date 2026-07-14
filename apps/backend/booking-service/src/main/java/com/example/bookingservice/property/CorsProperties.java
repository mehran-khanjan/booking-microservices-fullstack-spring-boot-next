package com.example.bookingservice.property;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for CORS allowed origins.
 *
 * <p>Binds to the {@code app.cors} prefix in application properties.
 *
 * <p>Example:
 *
 * <pre>{@code
 * app.cors.allowed-origins:
 *   - https://example.com
 *   - http://localhost:3000
 * }</pre>
 */
@Configuration
@ConfigurationProperties(prefix = "app.cors")
@Getter
@Setter
public class CorsProperties {
  /** The list of origins that are allowed to make cross‑origin requests. */
  private List<String> allowedOrigins;
}
