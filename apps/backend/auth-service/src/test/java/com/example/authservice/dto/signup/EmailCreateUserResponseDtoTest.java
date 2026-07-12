package com.example.authservice.dto.signup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailCreateUserResponseDtoTest {

  @Test
  void builder_setsUserId() {
    UUID id = UUID.randomUUID();

    EmailCreateUserResponseDto dto = EmailCreateUserResponseDto.builder().userId(id).build();

    assertThat(dto.getUserId()).isEqualTo(id);
  }

  @Test
  void setter_updatesUserId() {
    UUID initial = UUID.randomUUID();
    EmailCreateUserResponseDto dto = EmailCreateUserResponseDto.builder().userId(initial).build();
    UUID updated = UUID.randomUUID();

    dto.setUserId(updated);

    assertThat(dto.getUserId()).isEqualTo(updated);
  }

  @Test
  void equalsAndHashCode_matchForSameValue() {
    UUID id = UUID.randomUUID();
    EmailCreateUserResponseDto dto1 = EmailCreateUserResponseDto.builder().userId(id).build();
    EmailCreateUserResponseDto dto2 = EmailCreateUserResponseDto.builder().userId(id).build();

    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }

  @Test
  void toString_containsUserId() {
    UUID id = UUID.randomUUID();
    EmailCreateUserResponseDto dto = EmailCreateUserResponseDto.builder().userId(id).build();

    assertThat(dto.toString()).contains(id.toString());
  }
}
