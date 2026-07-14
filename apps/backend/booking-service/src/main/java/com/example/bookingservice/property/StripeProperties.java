package com.example.bookingservice.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds to {@code payment.stripe.*}. Populate {@code api-key} from an environment variable/secret
 * store in every non-local environment - never commit a live key.
 */
@Configuration
@ConfigurationProperties(prefix = "payment.stripe")
@Getter
@Setter
public class StripeProperties {

  /** Secret API key (sk_test_... / sk_live_...). */
  private String apiKey;

  /** Webhook signing secret, for verifying async Stripe webhook events. */
  private String webhookSecret;
}
