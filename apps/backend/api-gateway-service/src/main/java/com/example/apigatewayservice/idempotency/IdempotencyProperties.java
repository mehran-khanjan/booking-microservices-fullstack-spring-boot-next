package com.example.apigatewayservice.idempotency;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration properties for idempotency validation (prefix: {@code apigw.idempotency}). */
@Data
@Component
@ConfigurationProperties(prefix = "apigw.idempotency")
public class IdempotencyProperties {

  /** Enable or disable idempotency validation globally. */
  private boolean enabled = true;

  /**
   * Maximum allowed length for Idempotency-Key header. Default: 256 characters (matches Stripe's
   * limit).
   */
  private int maxLength = 256;

  /**
   * Whether to skip idempotency checks for diagnostic paths. Default: true (skip /actuator,
   * /swagger, /v3/api-docs).
   */
  private boolean skipDiagnosticPaths = true;
}
