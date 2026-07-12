package com.example.authservice.dto.signin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TokenResponseTest {

  @Test
  void builder_setsAllFields() {
    TokenResponse response =
        TokenResponse.builder().accessToken("at").refreshToken("rt").expiresIn(300).build();

    assertThat(response.accessToken()).isEqualTo("at");
    assertThat(response.refreshToken()).isEqualTo("rt");
    assertThat(response.expiresIn()).isEqualTo(300);
  }

  @Test
  void serializesToSnakeCaseJsonKeys() throws Exception {
    TokenResponse response =
        TokenResponse.builder().accessToken("at").refreshToken("rt").expiresIn(300).build();

    String json = new ObjectMapper().writeValueAsString(response);

    assertThat(json).contains("\"access_token\":\"at\"");
    assertThat(json).contains("\"refresh_token\":\"rt\"");
    assertThat(json).contains("\"expires_in\":300");
  }
}
