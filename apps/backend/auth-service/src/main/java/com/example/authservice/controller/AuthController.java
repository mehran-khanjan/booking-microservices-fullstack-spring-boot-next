package com.example.authservice.controller;

import com.example.authservice.dto.otp.PhoneOtpRequest;
import com.example.authservice.dto.otp.PhoneOtpVerifyRequest;
import com.example.authservice.dto.passwordchange.ChangePasswordRequest;
import com.example.authservice.dto.passwordforget.ForgotPasswordRequest;
import com.example.authservice.dto.signin.GoogleAuthRequest;
import com.example.authservice.dto.signin.PhoneLoginRequest;
import com.example.authservice.dto.signin.TokenResponse;
import com.example.authservice.dto.signup.EmailCreateUserResponseDto;
import com.example.authservice.dto.signup.EmailReadUserRequestDto;
import com.example.authservice.dto.signup.PhoneCreateUserResponseDto;
import com.example.authservice.dto.signup.VerifyUserEmailRequestDto;
import com.example.authservice.service.AuthService;
import com.example.commonlib.exception.UserAlreadyExistsException;
import com.example.commonlib.idempotency.Idempotent;
import com.example.commonlib.responseenvelope.response.ApiResponse;
import com.example.commonlib.route.ApiRoutes;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that handles all authentication-related operations for the authentication
 * service.
 *
 * <p>This controller provides endpoints for user registration (email and phone), sign-in (email,
 * phone, Google OAuth), email/phone verification, password reset, password change, token refresh,
 * and logout. It leverages Spring Security for authentication and JWT-based token management.
 *
 * <p>All endpoints return responses wrapped in a standardized {@link ApiResponse} envelope. Some
 * endpoints are protected by idempotency ({@link Idempotent}) and rate limiting ({@link
 * RateLimiter}) to prevent abuse. Refresh tokens are stored in HTTP-only, secure cookies for
 * enhanced security.
 *
 * <p>The controller uses the {@link AuthService} to delegate business logic. Multiple path aliases
 * are provided for backward compatibility (e.g., endpoints with and without a timestamp suffix).
 *
 * @author Your Team
 * @see AuthService
 * @see ApiResponse
 * @since 1.0
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

  /** Service that encapsulates authentication business logic. */
  private final AuthService authService;

  /** Name of the cookie used to store the refresh token (from application properties). */
  @Value("${token.cookie.name}")
  private String refreshTokenCookieName;

  /** Max age (in seconds) of the refresh token cookie. */
  @Value("${token.cookie.max-age}")
  private int cookieMaxAge;

  /** Whether the refresh token cookie should be flagged as secure (HTTPS only). */
  @Value("${token.cookie.secure}")
  private boolean cookieSecure;

  /**
   * Registers a new user using email and password.
   *
   * <p>This endpoint initiates the email-based sign-up process. The user is created in a pending
   * state, and an OTP is sent to the provided email for verification. The endpoint is idempotent
   * (within 1 hour) to avoid duplicate registrations.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/signup/email} and {@code
   * /api/v1/auth/signup/email/ts} are supported (the latter is for backward compatibility).
   *
   * @param request the sign-up request containing email and password (validated)
   * @return a {@link ResponseEntity} with:
   *     <ul>
   *       <li>HTTP 201 (Created) on success, with a {@code Location} header pointing to the new
   *           user resource and an {@code ApiResponse} containing the user ID
   *       <li>HTTP 409 (Conflict) if the email is already registered
   *     </ul>
   */
  @Idempotent(ttlSeconds = 3600)
  @RateLimiter(name = "globalRateLimiter")
  @PostMapping(path = {ApiRoutes.Auth.SIGN_UP_EMAIL, ApiRoutes.Auth.SIGN_UP_EMAIL_TS})
  public ResponseEntity<ApiResponse<EmailCreateUserResponseDto>> signUp(
      @Valid @RequestBody EmailReadUserRequestDto request) {
    try {
      String userId = authService.registerUser(request.email(), request.password());

      EmailCreateUserResponseDto res =
          EmailCreateUserResponseDto.builder().userId(UUID.fromString(userId)).build();

      return ResponseEntity.status(HttpStatus.CREATED)
          .header("Location", "/api/v1/users/" + userId)
          .body(
              ApiResponse.success(
                  HttpStatus.CREATED, "Registered. Please verify your email.", res));
    } catch (UserAlreadyExistsException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ApiResponse.conflict("User already exists", e.getMessage()));
    }
  }

  /**
   * Verifies the email OTP and completes the email-based sign-up process.
   *
   * <p>Upon successful OTP verification, the user account is activated, and a pair of JWT tokens
   * (access and refresh) is returned.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/signup/email/verify} and {@code
   * /api/v1/auth/signup/email/verify/ts} are supported.
   *
   * @param req the verification request containing email and OTP (validated)
   * @return a {@link ResponseEntity} with HTTP 200 and an {@code ApiResponse} containing the {@link
   *     TokenResponse} (access and refresh tokens)
   */
  @RateLimiter(name = "globalRateLimiter")
  @PostMapping(path = {ApiRoutes.Auth.SIGN_UP_EMAIL_VERIFY, ApiRoutes.Auth.SIGN_UP_EMAIL_VERIFY_TS})
  public ResponseEntity<ApiResponse<TokenResponse>> verifyEmail(
      @Valid @RequestBody VerifyUserEmailRequestDto req) {

    TokenResponse token = authService.verifyEmailOtp(req.email(), req.otp());

    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK, "Email verified successfully", token));
  }

  /**
   * Authenticates a user using email and password.
   *
   * <p>If successful, a new pair of JWT tokens is generated. The refresh token is set as an
   * HTTP-only, secure cookie in the response.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/signin/email} and {@code
   * /api/v1/auth/signin/email/ts} are supported.
   *
   * @param request the sign-in request containing email and password (validated)
   * @param response the HTTP response used to set the refresh token cookie
   * @return a {@link ResponseEntity} with HTTP 200 and an {@code ApiResponse} containing the {@link
   *     TokenResponse}
   */
  @RateLimiter(name = "globalRateLimiter")
  @PostMapping(path = {ApiRoutes.Auth.SIGN_IN_EMAIL, ApiRoutes.Auth.SIGN_IN_EMAIL_TS})
  public ResponseEntity<ApiResponse<TokenResponse>> signIn(
      @Valid @RequestBody EmailReadUserRequestDto request, HttpServletResponse response) {

    TokenResponse tokenResponse = authService.login(request.email(), null, request.password());

    setRefreshTokenCookie(response, tokenResponse.refreshToken());

    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Login successful", tokenResponse));
  }

  /**
   * Initiates the phone-based sign-up process by sending an OTP to the provided phone number.
   *
   * <p>The user is created in a pending state. The endpoint is idempotent to prevent duplicate OTP
   * requests within a 1-hour window.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/signup/phone} and {@code
   * /api/v1/auth/signup/phone/ts} are supported.
   *
   * @param request the request containing phone number and password (validated)
   * @return a {@link ResponseEntity} with HTTP 201 (Created) and an {@code ApiResponse} containing
   *     the new user ID
   */
  @Idempotent(ttlSeconds = 3600)
  @RateLimiter(name = "globalRateLimiter")
  @PostMapping(path = {ApiRoutes.Auth.SIGN_UP_PHONE, ApiRoutes.Auth.SIGN_UP_PHONE_TS})
  public ResponseEntity<ApiResponse<PhoneCreateUserResponseDto>> initiatePhoneSignUp(
      @Valid @RequestBody PhoneOtpRequest request) {

    String userId = authService.registerUserWithPhone(request.phoneNumber(), request.password());

    PhoneCreateUserResponseDto res = new PhoneCreateUserResponseDto(UUID.fromString(userId));

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED, "OTP sent to your phone", res));
  }

  /**
   * Verifies the phone OTP and completes the phone-based sign-up process.
   *
   * <p>Upon successful OTP verification, the user account is activated, and a pair of JWT tokens is
   * returned.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/signup/phone/verify} and {@code
   * /api/v1/auth/signup/phone/verify/ts} are supported.
   *
   * @param request the verification request containing phone number and OTP (validated)
   * @return a {@link ResponseEntity} with HTTP 200 and an {@code ApiResponse} containing the {@link
   *     TokenResponse}
   */
  @RateLimiter(name = "globalRateLimiter")
  @PostMapping(path = {ApiRoutes.Auth.SIGN_UP_PHONE_VERIFY, ApiRoutes.Auth.SIGN_UP_PHONE_VERIFY_TS})
  public ResponseEntity<ApiResponse<TokenResponse>> verifyPhoneAndSignUp(
      @Valid @RequestBody PhoneOtpVerifyRequest request) {
    TokenResponse token = authService.verifyPhoneOtp(request.phoneNumber(), request.otp());
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK, "Phone verified successfully", token));
  }

  /**
   * Authenticates a user using phone number and password.
   *
   * <p>If successful, a new pair of JWT tokens is generated. The refresh token is set as an
   * HTTP-only, secure cookie in the response.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/signin/phone} and {@code
   * /api/v1/auth/signin/phone/ts} are supported.
   *
   * @param request the sign-in request containing phone number and password (validated)
   * @param response the HTTP response used to set the refresh token cookie
   * @return a {@link ResponseEntity} with HTTP 200 and an {@code ApiResponse} containing the {@link
   *     TokenResponse}
   */
  @RateLimiter(name = "globalRateLimiter")
  @PostMapping(path = {ApiRoutes.Auth.SIGN_IN_PHONE, ApiRoutes.Auth.SIGN_IN_PHONE_TS})
  public ResponseEntity<ApiResponse<TokenResponse>> signInWithPhone(
      @Valid @RequestBody PhoneLoginRequest request, HttpServletResponse response) {

    TokenResponse tokenResponse =
        authService.login(null, request.phoneNumber(), request.password());

    setRefreshTokenCookie(response, tokenResponse.refreshToken());

    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Login successful", tokenResponse));
  }

  /**
   * Authenticates a user using a Google ID token (OAuth 2.0 / OpenID Connect).
   *
   * <p>If the token is valid, the user is either looked up or created automatically, and a pair of
   * JWT tokens is generated. The refresh token is set as an HTTP-only, secure cookie in the
   * response.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/signin/google} and {@code
   * /api/v1/auth/signin/google/ts} are supported.
   *
   * @param request the request containing the Google ID token (validated)
   * @param response the HTTP response used to set the refresh token cookie
   * @return a {@link ResponseEntity} with HTTP 200 and an {@code ApiResponse} containing the {@link
   *     TokenResponse}
   */
  @RateLimiter(name = "globalRateLimiter")
  @PostMapping(path = {ApiRoutes.Auth.SIGN_IN_GOOGLE, ApiRoutes.Auth.SIGN_IN_GOOGLE_TS})
  public ResponseEntity<ApiResponse<TokenResponse>> authenticateWithGoogle(
      @Valid @RequestBody GoogleAuthRequest request, HttpServletResponse response) {

    TokenResponse tokenResponse = authService.authenticateWithGoogle(request.idToken());

    setRefreshTokenCookie(response, tokenResponse.refreshToken());

    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Login successful", tokenResponse));
  }

  /**
   * Refreshes the access token using a valid refresh token.
   *
   * <p>The refresh token must be provided in the cookie named by the configured {@code
   * token.cookie.name}. If the token is valid, a new access token (and a new refresh token) are
   * issued, and the new refresh token is set in the cookie.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/refresh} and {@code
   * /api/v1/auth/refresh/ts} are supported.
   *
   * @param refreshToken the refresh token extracted from the cookie (may be null)
   * @param response the HTTP response used to set the new refresh token cookie
   * @return a {@link ResponseEntity} with:
   *     <ul>
   *       <li>HTTP 200 if the token is successfully refreshed, with an {@code ApiResponse}
   *           containing the new {@link TokenResponse}
   *       <li>HTTP 401 if the refresh token is missing
   *     </ul>
   */
  @PostMapping(path = {ApiRoutes.Auth.AUTH_REFRESH, ApiRoutes.Auth.AUTH_REFRESH_TS})
  public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
      @CookieValue(name = "${token.cookie.name}", required = false) String refreshToken,
      HttpServletResponse response) {
    if (refreshToken == null || refreshToken.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(
              ApiResponse.error(
                  HttpStatus.UNAUTHORIZED,
                  "No refresh token present",
                  HttpStatus.UNAUTHORIZED.toString(),
                  "No refresh token present"));
    }
    TokenResponse tokenResponse = authService.refreshToken(refreshToken);
    setRefreshTokenCookie(response, tokenResponse.refreshToken());
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Token refreshed", tokenResponse));
  }

  /**
   * Logs out the current user by invalidating the refresh token and clearing the cookie.
   *
   * <p>The refresh token is extracted from the cookie (if present) and passed to the authentication
   * service for invalidation. The cookie is then cleared.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/signout} and {@code
   * /api/v1/auth/signout/ts} are supported.
   *
   * @param refreshToken the refresh token from the cookie (may be null)
   * @param response the HTTP response used to clear the cookie
   * @return a {@link ResponseEntity} with HTTP 200 and an {@code ApiResponse} indicating successful
   *     logout
   */
  @PostMapping(path = {ApiRoutes.Auth.SIGN_OUT, ApiRoutes.Auth.SIGN_OUT_TS})
  public ResponseEntity<ApiResponse<Void>> signOut(
      @CookieValue(name = "${token.cookie.name}", required = false) String refreshToken,
      HttpServletResponse response) {
    if (refreshToken != null) authService.logout(refreshToken);
    clearRefreshTokenCookie(response);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Logged out successfully", null));
  }

  /**
   * Initiates a password reset process for the given email.
   *
   * <p>A password reset OTP is sent to the user's registered email address. For security reasons,
   * this endpoint always returns the same success message regardless of whether the email exists in
   * the system, thereby preventing user enumeration attacks.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/forget-password} and {@code
   * /api/v1/auth/forget-password/ts} are supported.
   *
   * @param request the request containing the email (validated)
   * @return a {@link ResponseEntity} with HTTP 200 and an {@code ApiResponse} indicating that a
   *     reset email was sent if the account exists
   */
  @RateLimiter(name = "globalRateLimiter")
  @PostMapping(path = {ApiRoutes.Auth.FORGET_PASSWORD, ApiRoutes.Auth.FORGET_PASSWORD_TS})
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    authService.initiatePasswordReset(request.email());
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK, "If the account exists, a reset email was sent", null));
    // Note: always return this generic message regardless of whether the email exists (prevents
    // user enumeration).
  }

  /**
   * Changes the password for the currently authenticated user.
   *
   * <p>The user must be authenticated (JWT token present in the request) and provide their old
   * password and a new password. The endpoint validates the old password before updating.
   *
   * <p><strong>Path aliases:</strong> Both {@code /api/v1/auth/change-password} and {@code
   * /api/v1/auth/change-password/ts} are supported.
   *
   * @param request the change password request containing old and new passwords (validated)
   * @param jwt the JWT token of the authenticated user (extracted from the Spring Security context)
   * @return a {@link ResponseEntity} with HTTP 200 and an {@code ApiResponse} indicating success
   */
  @PostMapping(path = {ApiRoutes.Auth.CHANGE_PASSWORD, ApiRoutes.Auth.CHANGE_PASSWORD_TS})
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Jwt jwt) {
    authService.changePassword(jwt.getSubject(), request.oldPassword(), request.newPassword());
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK, "Password changed successfully", null));
  }

  /**
   * Helper method to set the refresh token as an HTTP-only, secure cookie.
   *
   * @param response the HTTP response
   * @param refreshToken the refresh token to store
   */
  private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
    Cookie cookie = new Cookie(refreshTokenCookieName, refreshToken);
    cookie.setHttpOnly(true);
    cookie.setSecure(cookieSecure);
    cookie.setPath("/");
    cookie.setMaxAge(cookieMaxAge);
    cookie.setAttribute("SameSite", "Strict");
    response.addCookie(cookie);
  }

  /**
   * Helper method to clear the refresh token cookie by setting its max age to 0.
   *
   * @param response the HTTP response
   */
  private void clearRefreshTokenCookie(HttpServletResponse response) {
    Cookie cookie = new Cookie(refreshTokenCookieName, "");
    cookie.setHttpOnly(true);
    cookie.setSecure(cookieSecure);
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }
}
