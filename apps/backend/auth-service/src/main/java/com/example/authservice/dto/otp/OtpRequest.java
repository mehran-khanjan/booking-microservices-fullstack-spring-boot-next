package com.example.authservice.dto.otp;

import com.example.authservice.enums.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Generic request for generating or resending an OTP.
 *
 * <p>Used when the authentication service needs to send an OTP via a specific channel (e.g., EMAIL
 * or SMS) to a given identifier (email or phone number). The userId ties the request to a specific
 * user account.
 *
 * @param channel the delivery channel for the OTP (must not be null)
 * @param identifier the email address or phone number where the OTP should be sent (must not be
 *     blank)
 * @param userId the internal user ID associated with the request (must not be blank)
 */
public record OtpRequest(
    @NotNull OtpChannel channel, @NotBlank String identifier, @NotBlank String userId) {}
