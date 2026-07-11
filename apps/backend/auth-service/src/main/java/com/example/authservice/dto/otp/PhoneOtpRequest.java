package com.example.authservice.dto.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request for initiating phone-based sign-up or sending an OTP to a phone number.
 *
 * <p>Contains the phone number (in E.164 international format) and an optional password for the new
 * account. The phone number must match the {@code ^\+[1-9]\d{1,14}$} regex, which enforces a valid
 * international format with a leading plus sign.
 *
 * @param phoneNumber the user's phone number (validated by pattern)
 * @param password the password for the new account (may be null if not provided)
 */
public record PhoneOtpRequest(
    @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phoneNumber, String password) {}
