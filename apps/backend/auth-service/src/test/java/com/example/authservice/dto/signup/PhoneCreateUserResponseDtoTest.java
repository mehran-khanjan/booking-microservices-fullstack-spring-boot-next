package com.example.authservice.dto.signup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhoneCreateUserResponseDtoTest {

  @Test
  void accessor_returnsConstructorValue() {
    UUID id = UUID.randomUUID();

    PhoneCreateUserResponseDto dto = new PhoneCreateUserResponseDto(id);

    assertThat(dto.userId()).isEqualTo(id);
  }
}
