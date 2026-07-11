package com.example.authservice.dto.signup;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for verifying an email address with an OTP.
 *
 * <p>Used after email sign-up to complete the verification step. Both the email address and the OTP
 * are required and must not be blank.
 *
 * @param email the email address to verify
 * @param otp the one-time password sent to that email
 */
public record VerifyUserEmailRequestDto(@NotBlank String email, @NotBlank String otp) {}
