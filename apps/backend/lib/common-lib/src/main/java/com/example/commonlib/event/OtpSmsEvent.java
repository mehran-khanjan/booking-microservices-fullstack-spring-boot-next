package com.example.commonlib.event;

/**
 * Event representing an OTP (One‑Time Password) delivery via SMS.
 *
 * <p>This record is used to encapsulate the data needed to send an OTP SMS: the recipient's phone
 * number, the OTP code itself, and its validity duration.
 *
 * @param toPhoneNumber the recipient's phone number (in E.164 format)
 * @param otp the one‑time password to send
 * @param expiryMinutes the validity period of the OTP in minutes
 */
public record OtpSmsEvent(String toPhoneNumber, String otp, int expiryMinutes) {}
