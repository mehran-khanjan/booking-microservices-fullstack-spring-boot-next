package com.example.apigatewayservice.locale;

import com.example.commonlib.locale.AppLocale;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Accept-Language header validation (prefix: {@code apigw.locale}).
 */
@Data
@Component
@ConfigurationProperties(prefix = "apigw.locale")
public class LocaleProperties {

  private boolean enabled = true;
  private boolean required = true;
  private Set<String> supportedLanguages = Set.of();

  // CHANGE THIS LINE: now defaults to the static class
  private Set<String> supportedLocales = AppLocale.SUPPORTED_LOCALES;

  // CHANGE THIS LINE: now defaults to the static class
  private String defaultLocale = AppLocale.DEFAULT_LOCALE;

  private boolean skipDiagnosticPaths = true;
}
