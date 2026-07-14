package com.example.bookingservice.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds to {@code payment.paypal.*}. Use the sandbox base URL in dev/test and the live {@code
 * https://api-m.paypal.com} URL in production, driven by profile-specific config.
 */
@Configuration
@ConfigurationProperties(prefix = "payment.paypal")
@Getter
@Setter
public class PayPalProperties {

  private String clientId;
  private String clientSecret;
  private String baseUrl = "https://api-m.sandbox.paypal.com";
}
