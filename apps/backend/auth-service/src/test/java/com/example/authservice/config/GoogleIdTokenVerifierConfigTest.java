package com.example.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;

class GoogleIdTokenVerifierConfigTest {

  private final GoogleIdTokenVerifierConfig config = new GoogleIdTokenVerifierConfig();

  @Test
  void googleIdTokenVerifier_isBuilt_withConfiguredAudience() {
    GoogleIdTokenVerifier verifier =
        config.googleIdTokenVerifier("test-client-id.apps.googleusercontent.com");

    assertThat(verifier).isNotNull();
    assertThat(verifier.getAudience()).containsExactly("test-client-id.apps.googleusercontent.com");
  }
}
