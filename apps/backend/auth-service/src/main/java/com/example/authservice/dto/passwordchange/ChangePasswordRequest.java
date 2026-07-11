package com.example.authservice.dto.passwordchange;

/**
 * Request for changing a user's password.
 *
 * <p>Used when an authenticated user wants to update their password. Both the old password (for
 * verification) and the new password are required.
 *
 * @param oldPassword the user's current password
 * @param newPassword the desired new password
 */
public record ChangePasswordRequest(String oldPassword, String newPassword) {}
