package com.example.authservice.dto.signin;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for authenticating a user using a phone number and password.
 *
 * <p>Used in the phone-based sign-in flow. Both the phone number and password are required and must
 * not be blank.
 *
 * @param phoneNumber the user's phone number (in E.164 format)
 * @param password the user's password
 */
public record PhoneLoginRequest(@NotBlank String phoneNumber, @NotBlank String password) {}
