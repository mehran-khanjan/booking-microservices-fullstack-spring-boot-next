package com.example.commonlib.event;

/**
 * Event representing an OTP (One‑Time Password) delivery via email.
 *
 * <p>This record is used to encapsulate the data needed to send an OTP email: the recipient's email
 * address, the OTP code itself, and its validity duration.
 *
 * @param toEmail the recipient's email address
 * @param otp the one‑time password to send
 * @param expiryMinutes the validity period of the OTP in minutes
 */
public record OtpEmailEvent(String toEmail, String otp, int expiryMinutes) {}
