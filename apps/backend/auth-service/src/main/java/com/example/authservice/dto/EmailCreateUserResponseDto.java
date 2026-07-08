package com.example.authservice.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** Response DTO containing the newly created user's UUID after successful sign‑up. */
@Data
@Builder
public class EmailCreateUserResponseDto {
  private UUID userId;
}
