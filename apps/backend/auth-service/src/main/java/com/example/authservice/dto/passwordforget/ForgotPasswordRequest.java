package com.example.authservice.dto.passwordforget;

/**
 * Request for initiating a password reset process.
 *
 * <p>Contains the email address of the user who forgot their password. The authentication service
 * will send a password reset OTP to this address if it exists in the system. The email is optional
 * in this record, but validation is typically applied at the controller level.
 *
 * @param email the email address associated with the user account
 */
public record ForgotPasswordRequest(String email) {}
