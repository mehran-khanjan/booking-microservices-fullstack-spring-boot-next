package com.example.authservice.dto.otp;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for verifying a phone-based OTP.
 *
 * <p>Used during phone sign-up or phone verification flows. Contains the phone number and the OTP
 * code sent to that number. Both fields are required and must not be blank.
 *
 * @param phoneNumber the user's phone number in E.164 format (e.g., +1234567890)
 * @param otp the one-time password sent to the phone number
 */
public record PhoneOtpVerifyRequest(@NotBlank String phoneNumber, @NotBlank String otp) {}
