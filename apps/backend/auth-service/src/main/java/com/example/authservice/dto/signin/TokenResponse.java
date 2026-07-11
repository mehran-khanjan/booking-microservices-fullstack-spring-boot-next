package com.example.authservice.dto.signin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Response payload containing JWT tokens after successful authentication.
 *
 * <p>This DTO is returned by login and token refresh endpoints. It includes an access token (used
 * for API authorization), a refresh token (set as an HTTP-only cookie), and the expiration time (in
 * seconds) of the access token.
 *
 * @param accessToken the short-lived JWT access token (serialized as "access_token")
 * @param refreshToken the long-lived refresh token (serialized as "refresh_token"), intended to be
 *     stored in a secure cookie
 * @param expiresIn the validity period of the access token in seconds (serialized as "expires_in")
 */
@Builder
public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken, // Will be set as cookie
    @JsonProperty("expires_in") Integer expiresIn) {}
