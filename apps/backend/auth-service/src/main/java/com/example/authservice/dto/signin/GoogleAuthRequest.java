package com.example.authservice.dto.signin;

import jakarta.validation.constraints.NotBlank;

/**
 * Request for authenticating a user via Google OAuth 2.0 ID token.
 *
 * <p>Clients obtain an ID token from Google's authentication flow and send it to this endpoint for
 * verification and session creation.
 *
 * @param idToken the Google ID token (must not be blank)
 */
public record GoogleAuthRequest(@NotBlank String idToken) {}
