package com.example.authservice.dto.signup;

import java.util.UUID;

/**
 * Response DTO containing the newly created user's UUID after phone sign‑up initiation.
 *
 * <p>Returned when a phone-based sign-up request is accepted and an OTP has been sent. The UUID
 * identifies the pending user record.
 *
 * @param userId the unique identifier of the user created during phone sign-up
 */
public record PhoneCreateUserResponseDto(UUID userId) {}
