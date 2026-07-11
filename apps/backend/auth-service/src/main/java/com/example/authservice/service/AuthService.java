package com.example.authservice.service;

import com.example.authservice.dto.otp.OtpRequest;
import com.example.authservice.dto.signin.TokenResponse;
import com.example.authservice.enums.OtpChannel;
import com.example.authservice.feign.KeycloakAuthClient;
import com.example.authservice.service.keycloak.KeycloakTokenExchangeService;
import com.example.authservice.service.keycloak.KeycloakUserAdminService;
import com.example.authservice.service.otp.OtpService;
import com.example.commonlib.exception.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Core authentication service that orchestrates user registration, login, token management, and
 * third-party authentication (Google).
 *
 * <p>This service acts as the main entry point for all authentication-related operations. It
 * delegates to various specialized services:
 *
 * <ul>
 *   <li>{@link KeycloakUserAdminService} – for user CRUD operations in Keycloak
 *   <li>{@link KeycloakAuthClient} – for Keycloak OIDC token endpoints
 *   <li>{@link KeycloakTokenExchangeService} – for token exchange (acting on behalf of a user)
 *   <li>{@link PasswordValidationService} – for password strength validation
 *   <li>{@link AccountLockoutService} – for tracking failed login attempts and locking accounts
 *   <li>{@link TokenRevocationService} – for blacklisting revoked tokens
 *   <li>{@link GoogleIdTokenVerifier} – for verifying Google ID tokens
 *   <li>{@link OtpService} – for OTP generation and verification
 * </ul>
 *
 * <p>The service uses resilience patterns ({@link CircuitBreaker}, {@link Retry}) on critical
 * operations to handle failures gracefully. Fallback methods are provided for Keycloak-related
 * operations, distinguishing between business exceptions (which are rethrown) and infrastructure
 * failures (which result in a {@link KeycloakOperationException}).
 *
 * <p>Configuration is read from application properties:
 *
 * <ul>
 *   <li>{@code keycloak.realm} – Keycloak realm name
 *   <li>{@code keycloak.client-id} – OAuth2 client ID
 *   <li>{@code keycloak.client-secret} – OAuth2 client secret
 * </ul>
 *
 * @author Your Team
 * @see KeycloakUserAdminService
 * @see KeycloakTokenExchangeService
 * @see OtpService
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  /** Service for Keycloak user administration (create, update, find). */
  private final KeycloakUserAdminService keycloakUserAdmin;

  /** Feign client for Keycloak OIDC token and logout endpoints. */
  private final KeycloakAuthClient keycloakAuthClient;

  /** Service for exchanging service-account tokens for user-specific tokens. */
  private final KeycloakTokenExchangeService tokenExchangeService;

  /** Service for password strength validation and history checks. */
  private final PasswordValidationService passwordValidationService;

  /** Service for tracking failed logins and account lockout. */
  private final AccountLockoutService accountLockoutService;

  /** Service for revoking and checking revoked tokens in Redis. */
  private final TokenRevocationService tokenRevocationService;

  /** Google ID token verifier for Google OAuth authentication. */
  private final GoogleIdTokenVerifier googleIdTokenVerifier;

  /** Service for OTP generation, dispatch, and verification. */
  private final OtpService otpService;

  /** Keycloak realm name. */
  @Value("${keycloak.realm}")
  private String realm;

  /** Keycloak OAuth2 client ID. */
  @Value("${keycloak.client-id}")
  private String clientId;

  /** Keycloak OAuth2 client secret. */
  @Value("${keycloak.client-secret}")
  private String clientSecret;

  /**
   * Registers a new user using email and password.
   *
   * <p>This method validates the password strength, creates the user in Keycloak with the {@code
   * signupMethod} attribute set to {@code EMAIL} and {@code phoneVerified} set to {@code false}.
   * After user creation, an OTP is generated and sent to the provided email address via the OTP
   * service.
   *
   * <p>The user remains in an unverified state until the OTP is verified via {@link
   * #verifyEmailOtp(String, String)}.
   *
   * @param email the user's email address (must not be {@code null})
   * @param password the user's password (must meet policy requirements)
   * @return the Keycloak user ID of the newly created user
   * @throws PasswordPolicyException if the password does not meet the policy
   * @throws UserAlreadyExistsException if the email is already registered
   * @throws KeycloakOperationException if Keycloak communication fails
   */
  public String registerUser(String email, String password) {
    log.info("Registering user email={}", mask(email));
    passwordValidationService.validatePassword(password);

    String userId =
        keycloakUserAdmin.createUser(
            email,
            null,
            password,
            Map.of("signupMethod", List.of("EMAIL"), "phoneVerified", List.of("false")));

    otpService.generateAndSendOtp(new OtpRequest(OtpChannel.EMAIL, email, userId));

    return userId;
  }

  /**
   * Verifies the email OTP and activates the user account.
   *
   * <p>This method validates the OTP for the given email using {@link OtpService}. On successful
   * verification, it updates the user's email verification status to {@code true} in Keycloak and
   * exchanges the user ID for a pair of JWT tokens.
   *
   * @param email the email address associated with the OTP
   * @param otp the one-time password to verify
   * @return a {@link TokenResponse} containing access and refresh tokens
   * @throws InvalidOtpException if the OTP is invalid, expired, or max attempts exceeded
   * @throws OtpServiceUnavailableException if Redis is unavailable (fallback)
   * @throws KeycloakOperationException if Keycloak update or token exchange fails
   */
  public TokenResponse verifyEmailOtp(String email, String otp) {
    String userId = otpService.verifyOtp(OtpChannel.EMAIL, email, otp);

    keycloakUserAdmin.updateEmailVerified(userId, true);

    log.info("event=email_verified userId={}", userId);

    return tokenExchangeService.exchangeTokenForUser(userId);
  }

  /**
   * Registers a new user using phone number and password.
   *
   * <p>This method validates the password strength, creates the user in Keycloak with the {@code
   * signupMethod} attribute set to {@code PHONE} and {@code phoneVerified} set to {@code false}.
   * After user creation, an OTP is generated and sent to the provided phone number via SMS.
   *
   * <p>The user remains in an unverified state until the OTP is verified via {@link
   * #verifyPhoneOtp(String, String)}.
   *
   * @param phoneNumber the user's phone number (in E.164 format)
   * @param password the user's password (must meet policy requirements)
   * @return the Keycloak user ID of the newly created user
   * @throws PasswordPolicyException if the password does not meet the policy
   * @throws UserAlreadyExistsException if the phone number is already registered
   * @throws KeycloakOperationException if Keycloak communication fails
   */
  public String registerUserWithPhone(String phoneNumber, String password) {
    log.info("Registering user phone={}", mask(phoneNumber));
    passwordValidationService.validatePassword(password);

    String userId =
        keycloakUserAdmin.createUser(
            null,
            phoneNumber,
            password,
            Map.of("signupMethod", List.of("PHONE"), "phoneVerified", List.of("false")));

    otpService.generateAndSendOtp(new OtpRequest(OtpChannel.PHONE, phoneNumber, userId));

    return userId;
  }

  /**
   * Verifies the phone OTP and activates the user account.
   *
   * <p>This method validates the OTP for the given phone number using {@link OtpService}. On
   * successful verification, it updates the user's {@code phoneVerified} attribute to {@code true}
   * in Keycloak and exchanges the user ID for a pair of JWT tokens.
   *
   * @param phoneNumber the phone number associated with the OTP
   * @param otp the one-time password to verify
   * @return a {@link TokenResponse} containing access and refresh tokens
   * @throws InvalidOtpException if the OTP is invalid, expired, or max attempts exceeded
   * @throws OtpServiceUnavailableException if Redis is unavailable (fallback)
   * @throws KeycloakOperationException if Keycloak update or token exchange fails
   */
  public TokenResponse verifyPhoneOtp(String phoneNumber, String otp) {
    String userId = otpService.verifyOtp(OtpChannel.PHONE, phoneNumber, otp);
    keycloakUserAdmin.setUserAttribute(userId, "phoneVerified", "true");
    log.info("event=phone_verified userId={}", userId);
    return tokenExchangeService.exchangeTokenForUser(userId);
  }

  /**
   * Authenticates a user using email or phone and password via Keycloak's password grant.
   *
   * <p>This method first checks if the account is locked using {@link
   * AccountLockoutService#checkAccountLocked(String)}. If the account is not locked, it sends a
   * password grant request to Keycloak. Upon successful authentication, the failed attempt counter
   * is reset. Additionally, for email-based login, it verifies that the email is verified in
   * Keycloak; for phone-based login, it verifies that the {@code phoneVerified} attribute is {@code
   * true}. If the account is not verified, an {@link AccountNotVerifiedException} is thrown.
   *
   * <p>On failure (e.g., invalid credentials), the failed attempt counter is incremented using
   * {@link AccountLockoutService#recordFailedLogin(String)}. If the maximum attempts are reached,
   * the account is locked.
   *
   * <p>The method is protected by a circuit breaker and retry for Keycloak calls. The fallback
   * handles business exceptions and infrastructure failures.
   *
   * @param email the user's email (may be {@code null} if phone is provided)
   * @param phone the user's phone number (may be {@code null} if email is provided)
   * @param password the user's password
   * @return a {@link TokenResponse} containing access and refresh tokens
   * @throws IllegalArgumentException if both email and phone are {@code null}
   * @throws AccountLockedException if the account is currently locked
   * @throws AccountNotVerifiedException if the account is not verified
   * @throws AuthenticationException if credentials are invalid
   * @throws KeycloakOperationException if Keycloak is unavailable (fallback)
   */
  @CircuitBreaker(name = "keycloak", fallbackMethod = "loginFallback")
  public TokenResponse login(String email, String phone, String password) {

    String identifier = email != null ? email : phone;
    if (identifier == null) throw new IllegalArgumentException("email or phone is required");

    log.info("Login attempt identifier={}", mask(identifier));
    accountLockoutService.checkAccountLocked(identifier);

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("username", identifier);
    formData.add("grant_type", "password");
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    formData.add("password", password);

    try {
      Map<String, Object> response = keycloakAuthClient.getToken(realm, formData);
      accountLockoutService.resetFailedAttempts(identifier);

      UserRepresentation user = keycloakUserAdmin.findByUsername(identifier);
      if (email != null) {
        // Email-based login: check Keycloak's built-in emailVerified flag
        if (!Boolean.TRUE.equals(user.isEmailVerified())) {
          throw new AccountNotVerifiedException(
              "Email not verified. Please verify your email before logging in.");
        }
      } else { // phone-based login
        Map<String, List<String>> attrs = user.getAttributes();
        String phoneVerified =
            (attrs != null && attrs.containsKey("phoneVerified"))
                ? attrs.get("phoneVerified").get(0)
                : "false";
        if (!"true".equals(phoneVerified)) {
          throw new AccountNotVerifiedException(
              "Phone not verified. Please verify your phone before logging in.");
        }
      }

      log.info("event=login_success identifier={}", mask(identifier));
      return toTokenResponse(response);
    } catch (FeignException.Unauthorized e) {
      accountLockoutService.recordFailedLogin(identifier);
      log.warn("event=login_failed identifier={}", mask(identifier));
      throw new AuthenticationException("Invalid credentials");
    } catch (FeignException e) {
      log.error("event=keycloak_error status={}", e.status());
      throw new KeycloakOperationException("Keycloak service unavailable");
    }
  }

  /**
   * Fallback method for {@link #login(String, String, String)} when the circuit breaker opens or
   * retries are exhausted.
   *
   * @param email the email (original argument)
   * @param phone the phone (original argument)
   * @param password the password (original argument)
   * @param t the cause
   * @return never returns normally
   * @throws RuntimeException (business exception or {@link KeycloakOperationException})
   */
  private TokenResponse loginFallback(String email, String phone, String password, Throwable t) {
    log.error("event=login_circuit_open", t);

    String identifier = email != null ? email : phone;

    if (t instanceof IllegalArgumentException
        || t instanceof AuthenticationException
        || t instanceof AccountLockedException
        || t instanceof AccountNotVerifiedException) {
      log.warn(
          "event=login_circuit_rethrow identifier={} exception={}",
          mask(identifier),
          t.getClass().getSimpleName());
      throw (RuntimeException) t;
    }

    throw new KeycloakOperationException(
        "Authentication service temporarily unavailable. Please try again later.");
  }

  /**
   * Refreshes the access token using a valid refresh token.
   *
   * <p>This method first checks if the refresh token has been revoked using {@link
   * TokenRevocationService#isTokenRevoked(String)}. If not, it calls Keycloak's token endpoint with
   * the {@code grant_type=refresh_token} to obtain a new access token and refresh token pair.
   *
   * <p>The method is protected by a circuit breaker and retry. The fallback handles {@link
   * AuthenticationException} (rethrows) and infrastructure failures (throws {@link
   * KeycloakOperationException}).
   *
   * @param refreshToken the refresh token to use
   * @return a new {@link TokenResponse} containing fresh tokens
   * @throws AuthenticationException if the token is revoked or the refresh request fails
   * @throws KeycloakOperationException if Keycloak is unavailable (fallback)
   */
  @CircuitBreaker(name = "keycloak", fallbackMethod = "refreshTokenFallback")
  @Retry(name = "keycloak")
  public TokenResponse refreshToken(String refreshToken) {
    if (tokenRevocationService.isTokenRevoked(refreshToken)) {
      throw new AuthenticationException("Token has been revoked");
    }
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "refresh_token");
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    formData.add("refresh_token", refreshToken);

    try {
      return toTokenResponse(keycloakAuthClient.getToken(realm, formData));
    } catch (FeignException e) {
      log.error("event=refresh_failed status={}", e.status());
      throw new AuthenticationException("Token refresh failed");
    }
  }

  /**
   * Fallback method for {@link #refreshToken(String)}.
   *
   * @param refreshToken the original token
   * @param t the cause
   * @return never returns normally
   * @throws AuthenticationException if the original exception was of that type
   * @throws KeycloakOperationException for all other failures
   */
  private TokenResponse refreshTokenFallback(String refreshToken, Throwable t) {
    log.error("event=refresh_circuit_open", t);

    if (t instanceof AuthenticationException) {
      log.warn("event=refresh_circuit_rethrow exception=AuthenticationException");
      throw (AuthenticationException) t;
    }

    throw new KeycloakOperationException("Authentication service temporarily unavailable.");
  }

  /**
   * Logs out the user by revoking the refresh token locally and calling Keycloak's logout endpoint.
   *
   * <p>The refresh token is immediately added to the revocation blacklist using {@link
   * TokenRevocationService#revokeToken(String)}. Then, if the token is valid, a logout request is
   * sent to Keycloak to invalidate the token on the server side. Any error from Keycloak is logged
   * but does not affect the local revocation.
   *
   * <p>The method is protected by a circuit breaker; the fallback logs the error but does not
   * rethrow business exceptions (the token is already revoked locally).
   *
   * @param refreshToken the refresh token to revoke (may be {@code null} or empty)
   */
  @CircuitBreaker(name = "keycloak", fallbackMethod = "logoutFallback")
  public void logout(String refreshToken) {
    if (refreshToken == null || refreshToken.isEmpty()) return;
    tokenRevocationService.revokeToken(refreshToken);

    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    formData.add("refresh_token", refreshToken);
    try {
      keycloakAuthClient.logout(realm, formData);
    } catch (FeignException e) {
      log.error("event=logout_error status={}", e.status());
    }
  }

  /**
   * Fallback method for {@link #logout(String)}.
   *
   * <p>The token has already been revoked locally, so we simply log the error. If the original
   * exception was an {@link AuthenticationException}, it is rethrown to preserve the semantic
   * (though uncommon in this context).
   *
   * @param refreshToken the original token
   * @param t the cause
   */
  private void logoutFallback(String refreshToken, Throwable t) {
    log.error("event=logout_circuit_open", t);
    // token already revoked locally — safe to swallow
    // If revocation itself threw a business exception (e.g., invalid token), rethrow it.
    if (t instanceof AuthenticationException) {
      throw (AuthenticationException) t;
    }
    // Otherwise, just log and continue (logout is idempotent on our side)
  }

  /**
   * Authenticates a user using a Google ID token (OAuth2 / OpenID Connect).
   *
   * <p>This method verifies the Google ID token using {@link GoogleIdTokenVerifier}. It extracts
   * the user's email and Google subject ID. If the email is not verified by Google, an {@link
   * EmailNotVerifiedByGoogleException} is thrown.
   *
   * <p>If no user exists with that email, a new Keycloak user is created with the Google identity
   * linked. If a user exists, the Google identity is linked if not already present. In both cases,
   * a token exchange is performed to obtain a Keycloak token for the user.
   *
   * <p>The method is protected by a circuit breaker and retry. The fallback handles all business
   * exceptions (rethrows) and infrastructure failures (throws {@link KeycloakOperationException}).
   *
   * @param idToken the Google ID token string
   * @return a {@link TokenResponse} containing access and refresh tokens
   * @throws GoogleTokenInvalidException if the token is invalid or verification fails
   * @throws EmailNotVerifiedByGoogleException if the Google email is not verified
   * @throws AccountLinkingConflictException if an unverified account with the same email exists
   * @throws KeycloakOperationException if Keycloak is unavailable (fallback)
   */
  @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "googleAuthFallback")
  @Retry(name = "keycloakAdmin")
  public TokenResponse authenticateWithGoogle(String idToken) {

    GoogleIdToken.Payload payload = verifyGoogleToken(idToken);

    String email = payload.getEmail();

    if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
      throw new EmailNotVerifiedByGoogleException("Google email not verified");
    }

    List<UserRepresentation> found = keycloakUserAdmin.findByEmail(email);
    UserRepresentation user;
    boolean isNewUser = found.isEmpty();

    if (isNewUser) {
      user =
          keycloakUserAdmin.createGoogleUser(
              email,
              (String) payload.get("given_name"),
              (String) payload.get("family_name"),
              payload.getSubject());
      publishUserRegisteredEvent(user);
    } else {
      user = found.get(0);
      linkGoogleIfNeeded(user, payload.getSubject(), email);
    }

    TokenResponse token = tokenExchangeService.exchangeTokenForUser(user.getId());
    log.info("event=google_auth_success userId={} newUser={}", user.getId(), isNewUser);
    return token;
  }

  /**
   * Links the Google federated identity to an existing Keycloak user if not already linked.
   *
   * <p>If the user does not have a Google federated identity, the method checks whether the user's
   * email is verified in Keycloak. If not, it throws an {@link AccountLinkingConflictException}
   * because the account must be verified before linking. Otherwise, it links the Google identity.
   *
   * @param user the Keycloak user representation
   * @param googleSub the Google subject ID
   * @param email the user's email
   * @throws AccountLinkingConflictException if the account is unverified
   */
  private void linkGoogleIfNeeded(UserRepresentation user, String googleSub, String email) {
    if (keycloakUserAdmin.hasFederatedIdentity(user.getId(), "google")) return;

    if (!Boolean.TRUE.equals(user.isEmailVerified())) {
      throw new AccountLinkingConflictException(
          "An account with this email already exists and is not verified. Verify it before using Google Sign-In.");
    }
    keycloakUserAdmin.linkFederatedIdentity(user.getId(), googleSub, email);
    log.info("event=google_identity_linked userId={}", user.getId());
  }

  /**
   * Verifies the Google ID token using the configured verifier.
   *
   * @param idTokenString the raw ID token string
   * @return the parsed {@link GoogleIdToken.Payload}
   * @throws GoogleTokenInvalidException if the token is invalid or verification fails
   */
  private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
    try {
      GoogleIdToken idToken = googleIdTokenVerifier.verify(idTokenString);
      if (idToken == null) throw new GoogleTokenInvalidException("Invalid Google token");
      return idToken.getPayload();
    } catch (GeneralSecurityException | IOException e) {
      throw new GoogleTokenInvalidException("Google token verification failed");
    }
  }

  /**
   * Publishes a user registered event via the outbox.
   *
   * <p><strong>Note:</strong> This method is currently a placeholder. It should be implemented to
   * publish an event (e.g., {@code user.registered}) to RabbitMQ using the transactional outbox
   * pattern.
   *
   * @param user the newly created user representation
   */
  private void publishUserRegisteredEvent(UserRepresentation user) {
    // TODO: publish via transactional outbox -> RabbitMQ (user.registered)
  }

  /**
   * Fallback method for {@link #authenticateWithGoogle(String)}.
   *
   * @param idToken the original ID token
   * @param t the cause
   * @return never returns normally
   * @throws RuntimeException (business exception or {@link KeycloakOperationException})
   */
  private TokenResponse googleAuthFallback(String idToken, Throwable t) {
    log.error("event=google_auth_circuit_open", t);

    // Rethrow all business exceptions from the real method
    if (t instanceof GoogleTokenInvalidException
        || t instanceof EmailNotVerifiedByGoogleException
        || t instanceof AccountLinkingConflictException
        || t instanceof AuthenticationException) {
      log.warn("event=google_auth_circuit_rethrow exception={}", t.getClass().getSimpleName());
      throw (RuntimeException) t;
    }

    throw new KeycloakOperationException(
        "Google sign-in temporarily unavailable, please try again");
  }

  /**
   * Initiates a password reset process for the given email.
   *
   * <p><strong>Note:</strong> This method is currently a placeholder and always throws {@link
   * UnsupportedOperationException}. The implementation should generate a reset token, store it in
   * Redis, and queue an email event via the outbox.
   *
   * @param email the email of the user requesting a password reset
   * @throws UnsupportedOperationException always thrown (not yet implemented)
   */
  public void initiatePasswordReset(String email) {
    log.info("Password reset initiated for email={}", mask(email));
    // TODO: generate reset token in Redis + send via notification-service
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /**
   * Changes the password for the given user after validating the old password and policy.
   *
   * <p><strong>Note:</strong> This method is currently a placeholder and always throws {@link
   * UnsupportedOperationException}. The implementation should verify the old password against
   * Keycloak, validate the new password against policy and history, and then update the credential
   * in Keycloak.
   *
   * @param userId the ID of the user
   * @param oldPassword the current password (for verification)
   * @param newPassword the desired new password
   * @throws PasswordPolicyException if the new password does not meet policy or is in history
   * @throws UnsupportedOperationException always thrown (not yet implemented)
   */
  public void changePassword(String userId, String oldPassword, String newPassword) {
    passwordValidationService.validatePassword(newPassword);
    if (!passwordValidationService.checkPasswordHistory(userId, newPassword)) {
      throw new PasswordPolicyException("Cannot reuse recent passwords");
    }
    // TODO: verify oldPassword against Keycloak, then update credential
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /**
   * Converts a Keycloak token response map to a {@link TokenResponse} DTO.
   *
   * @param response the raw map from Keycloak (must contain {@code access_token}, {@code
   *     refresh_token}, and {@code expires_in})
   * @return a constructed {@link TokenResponse}
   */
  private TokenResponse toTokenResponse(Map<String, Object> response) {
    return TokenResponse.builder()
        .accessToken((String) response.get("access_token"))
        .refreshToken((String) response.get("refresh_token"))
        .expiresIn(((Number) response.get("expires_in")).intValue())
        .build();
  }

  /**
   * Masks sensitive identifiers (email or phone) for logging.
   *
   * <p>For emails, shows the first two characters and the domain. For phone numbers, shows the last
   * 4 digits.
   *
   * @param value the string to mask (may be {@code null})
   * @return the masked string, or "***" if input is {@code null}
   */
  private String mask(String value) {
    if (value == null) return "***";
    if (value.contains("@")) {
      String[] parts = value.split("@");
      return parts[0].substring(0, Math.min(2, parts[0].length())) + "***@" + parts[1];
    }
    return value.length() < 4 ? "****" : "****" + value.substring(value.length() - 4);
  }
}
