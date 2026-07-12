package com.example.authservice.service;

import com.example.authservice.dto.otp.OtpRequest;
import com.example.authservice.feign.KeycloakAuthClient;
import com.example.authservice.service.keycloak.KeycloakTokenExchangeService;
import com.example.authservice.service.keycloak.KeycloakUserAdminService;
import java.util.List;
import java.util.Map;

import com.example.authservice.service.otp.EmailService;
import com.example.authservice.service.otp.OtpService;
import com.example.outboxlib.outbox.service.OutboxService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.example.authservice.dto.signin.TokenResponse;
import com.example.authservice.enums.OtpChannel;
import com.example.commonlib.exception.AccountNotVerifiedException;
import com.example.commonlib.exception.AuthenticationException;
import com.example.commonlib.exception.KeycloakOperationException;
import com.example.commonlib.exception.PasswordPolicyException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RequestTemplate;
import feign.FeignException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String REALM = "booking-app-realm";
  private static final String CLIENT_ID = "auth-service";
  private static final String CLIENT_SECRET = "secret";

  @Mock private KeycloakUserAdminService keycloakUserAdminService;

  @Captor private ArgumentCaptor<Map<String, List<String>>> attributesCaptor;

  @Mock private KeycloakUserAdminService keycloakUserAdmin;
  @Mock private KeycloakAuthClient keycloakAuthClient;
  @Mock private KeycloakTokenExchangeService tokenExchangeService;
  @Mock private PasswordValidationService passwordValidationService;
  @Mock private AccountLockoutService accountLockoutService;
  @Mock private TokenRevocationService tokenRevocationService;
  @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
  @Mock private OtpService otpService;
  @Mock private OutboxService outboxService;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private EmailService emailService;

  @Captor private ArgumentCaptor<OtpRequest> otpRequestCaptor;
  @Captor private ArgumentCaptor<MultiValueMap<String, String>> formDataCaptor;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
            new AuthService(
                    keycloakUserAdmin,
                    keycloakAuthClient,
                    tokenExchangeService,
                    passwordValidationService,
                    accountLockoutService,
                    tokenRevocationService,
                    googleIdTokenVerifier,
                    otpService,
                    outboxService,
                    redisTemplate,
                    emailService);
    ReflectionTestUtils.setField(authService, "realm", REALM);
    ReflectionTestUtils.setField(authService, "clientId", CLIENT_ID);
    ReflectionTestUtils.setField(authService, "clientSecret", CLIENT_SECRET);
  }

  @Test
  void registerUser_delegatesToKeycloakAdminService_andReturnsUserId() {
    when(keycloakUserAdmin.createUser(
            eq("user@example.com"), isNull(), eq("Passw0rd!"), anyMap()))
            .thenReturn("generated-user-id");

    String userId = authService.registerUser("user@example.com", "Passw0rd!");

    assertThat(userId).isEqualTo("generated-user-id");
  }

  @Test
  void registerUser_passesNullPhone() {
    when(keycloakUserAdmin.createUser(any(), isNull(), any(), anyMap())).thenReturn("id");

    authService.registerUser("user@example.com", "pw");

    verify(keycloakUserAdmin)
            .createUser(eq("user@example.com"), isNull(), eq("pw"), anyMap());
  }

  @Test
  void registerUser_validatesPassword_createsKeycloakUser_andDispatchesEmailOtp() {
    when(keycloakUserAdmin.createUser(eq("user@example.com"), isNull(), eq("Passw0rd!"), anyMap()))
            .thenReturn("user-id-1");

    String userId = authService.registerUser("user@example.com", "Passw0rd!");

    assertThat(userId).isEqualTo("user-id-1");
    verify(passwordValidationService).validatePassword("Passw0rd!");
    verify(otpService).generateAndSendOtp(otpRequestCaptor.capture());

    OtpRequest sent = otpRequestCaptor.getValue();
    assertThat(sent.channel()).isEqualTo(OtpChannel.EMAIL);
    assertThat(sent.identifier()).isEqualTo("user@example.com");
    assertThat(sent.userId()).isEqualTo("user-id-1");
  }

  @Test
  void registerUser_setsSignupMethodAndPhoneVerifiedAttributes() {
    when(keycloakUserAdmin.createUser(any(), isNull(), any(), anyMap())).thenReturn("user-id");

    authService.registerUser("user@example.com", "Passw0rd!");

    verify(keycloakUserAdmin)
            .createUser(
                    eq("user@example.com"),
                    isNull(),
                    eq("Passw0rd!"),
                    eq(Map.of("signupMethod", List.of("EMAIL"), "phoneVerified", List.of("false"))));
  }

  @Test
  void registerUser_invalidPassword_propagatesExceptionWithoutCreatingUser() {
    doThrow(new PasswordPolicyException("too weak"))
            .when(passwordValidationService)
            .validatePassword("weak");

    assertThatThrownBy(() -> authService.registerUser("user@example.com", "weak"))
            .isInstanceOf(PasswordPolicyException.class);

    verifyNoInteractions(keycloakUserAdmin, otpService);
  }

  // ---------------------------------------------------------------------
  // registerUserWithPhone
  // ---------------------------------------------------------------------

  @Test
  void registerUserWithPhone_createsUser_andDispatchesPhoneOtp() {
    when(keycloakUserAdmin.createUser(isNull(), eq("+15551234567"), eq("Passw0rd!"), anyMap()))
            .thenReturn("user-id-2");

    String userId = authService.registerUserWithPhone("+15551234567", "Passw0rd!");

    assertThat(userId).isEqualTo("user-id-2");
    verify(otpService).generateAndSendOtp(otpRequestCaptor.capture());
    assertThat(otpRequestCaptor.getValue().channel()).isEqualTo(OtpChannel.PHONE);
    assertThat(otpRequestCaptor.getValue().identifier()).isEqualTo("+15551234567");
  }

  // ---------------------------------------------------------------------
  // verifyEmailOtp / verifyPhoneOtp
  // ---------------------------------------------------------------------

  @Test
  void verifyEmailOtp_marksEmailVerified_andExchangesToken() {
    when(otpService.verifyOtp(OtpChannel.EMAIL, "user@example.com", "123456"))
            .thenReturn("user-id-3");
    TokenResponse expected = TokenResponse.builder().accessToken("at").build();
    when(tokenExchangeService.exchangeTokenForUser("user-id-3")).thenReturn(expected);

    TokenResponse result = authService.verifyEmailOtp("user@example.com", "123456");

    assertThat(result).isEqualTo(expected);
    verify(keycloakUserAdmin).updateEmailVerified("user-id-3", true);
  }

  @Test
  void verifyPhoneOtp_marksPhoneVerifiedAttribute_andExchangesToken() {
    when(otpService.verifyOtp(OtpChannel.PHONE, "+15551234567", "654321")).thenReturn("user-id-4");
    TokenResponse expected = TokenResponse.builder().accessToken("at2").build();
    when(tokenExchangeService.exchangeTokenForUser("user-id-4")).thenReturn(expected);

    TokenResponse result = authService.verifyPhoneOtp("+15551234567", "654321");

    assertThat(result).isEqualTo(expected);
    verify(keycloakUserAdmin).setUserAttribute("user-id-4", "phoneVerified", "true");
  }

  // ---------------------------------------------------------------------
  // login
  // ---------------------------------------------------------------------

  @Test
  void login_withVerifiedEmail_succeeds_andResetsFailedAttempts() {
    when(keycloakAuthClient.getToken(eq(REALM), any()))
            .thenReturn(Map.of("access_token", "at", "refresh_token", "rt", "expires_in", 300));
    UserRepresentation user = new UserRepresentation();
    user.setEmailVerified(true);
    when(keycloakUserAdmin.findByUsername("user@example.com")).thenReturn(user);

    TokenResponse result = authService.login("user@example.com", null, "Passw0rd!");

    assertThat(result.accessToken()).isEqualTo("at");
    assertThat(result.refreshToken()).isEqualTo("rt");
    assertThat(result.expiresIn()).isEqualTo(300);
    verify(accountLockoutService).checkAccountLocked("user@example.com");
    verify(accountLockoutService).resetFailedAttempts("user@example.com");
  }

  @Test
  void login_withUnverifiedEmail_throwsAccountNotVerified() {
    when(keycloakAuthClient.getToken(eq(REALM), any()))
            .thenReturn(Map.of("access_token", "at", "refresh_token", "rt", "expires_in", 300));
    UserRepresentation user = new UserRepresentation();
    user.setEmailVerified(false);
    when(keycloakUserAdmin.findByUsername("user@example.com")).thenReturn(user);

    assertThatThrownBy(() -> authService.login("user@example.com", null, "Passw0rd!"))
            .isInstanceOf(AccountNotVerifiedException.class);
  }

  @Test
  void login_withVerifiedPhone_succeeds() {
    when(keycloakAuthClient.getToken(eq(REALM), any()))
            .thenReturn(Map.of("access_token", "at", "refresh_token", "rt", "expires_in", 300));
    UserRepresentation user = new UserRepresentation();
    user.setAttributes(Map.of("phoneVerified", List.of("true")));
    when(keycloakUserAdmin.findByUsername("+15551234567")).thenReturn(user);

    TokenResponse result = authService.login(null, "+15551234567", "Passw0rd!");

    assertThat(result.accessToken()).isEqualTo("at");
  }

  @Test
  void login_withUnverifiedPhone_throwsAccountNotVerified() {
    when(keycloakAuthClient.getToken(eq(REALM), any()))
            .thenReturn(Map.of("access_token", "at", "refresh_token", "rt", "expires_in", 300));
    UserRepresentation user = new UserRepresentation();
    user.setAttributes(Map.of());
    when(keycloakUserAdmin.findByUsername("+15551234567")).thenReturn(user);

    assertThatThrownBy(() -> authService.login(null, "+15551234567", "Passw0rd!"))
            .isInstanceOf(AccountNotVerifiedException.class);
  }

  @Test
  void login_withoutEmailOrPhone_throwsIllegalArgument() {
    assertThatThrownBy(() -> authService.login(null, null, "Passw0rd!"))
            .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(accountLockoutService, keycloakAuthClient);
  }

  @Test
  void login_invalidCredentials_recordsFailedLogin_andThrowsAuthenticationException() {
    when(keycloakAuthClient.getToken(eq(REALM), any())).thenThrow(unauthorized());

    assertThatThrownBy(() -> authService.login("user@example.com", null, "wrong"))
            .isInstanceOf(AuthenticationException.class);

    verify(accountLockoutService).recordFailedLogin("user@example.com");
  }

  @Test
  void login_keycloakUnavailable_throwsKeycloakOperationException() {
    when(keycloakAuthClient.getToken(eq(REALM), any())).thenThrow(serverError());

    assertThatThrownBy(() -> authService.login("user@example.com", null, "Passw0rd!"))
            .isInstanceOf(KeycloakOperationException.class);

    verify(accountLockoutService, never()).recordFailedLogin(anyString());
  }

  // ---------------------------------------------------------------------
  // refreshToken
  // ---------------------------------------------------------------------

  @Test
  void refreshToken_notRevoked_returnsNewToken() {
    when(tokenRevocationService.isTokenRevoked("refresh-token")).thenReturn(false);
    when(keycloakAuthClient.getToken(eq(REALM), any()))
            .thenReturn(Map.of("access_token", "new-at", "refresh_token", "new-rt", "expires_in", 300));

    TokenResponse result = authService.refreshToken("refresh-token");

    assertThat(result.accessToken()).isEqualTo("new-at");
  }

  @Test
  void refreshToken_revoked_throwsAuthenticationException() {
    when(tokenRevocationService.isTokenRevoked("refresh-token")).thenReturn(true);

    assertThatThrownBy(() -> authService.refreshToken("refresh-token"))
            .isInstanceOf(AuthenticationException.class);

    verifyNoInteractions(keycloakAuthClient);
  }

  // ---------------------------------------------------------------------
  // logout
  // ---------------------------------------------------------------------

  @Test
  void logout_withToken_revokesLocally_andCallsKeycloakLogout() {
    authService.logout("refresh-token");

    verify(tokenRevocationService).revokeToken("refresh-token");
    verify(keycloakAuthClient).logout(eq(REALM), any());
  }

  @Test
  void logout_withNullToken_isNoOp() {
    authService.logout(null);

    verifyNoInteractions(tokenRevocationService, keycloakAuthClient);
  }

  @Test
  void logout_withEmptyToken_isNoOp() {
    authService.logout("");

    verifyNoInteractions(tokenRevocationService, keycloakAuthClient);
  }

  // ---------------------------------------------------------------------
  // authenticateWithGoogle
  // ---------------------------------------------------------------------

  @Test
  void authenticateWithGoogle_invalidToken_throwsGoogleTokenInvalidException() throws Exception {
    when(googleIdTokenVerifier.verify("bad-token")).thenReturn(null);

    assertThatThrownBy(() -> authService.authenticateWithGoogle("bad-token"))
            .isInstanceOf(com.example.commonlib.exception.GoogleTokenInvalidException.class);
  }

  // ---------------------------------------------------------------------
  // Not-yet-implemented flows
  // ---------------------------------------------------------------------

  @Test
  void changePassword_reusedPassword_throwsPasswordPolicyException() {
    when(passwordValidationService.checkPasswordHistory("user-id", "OldPassw0rd!")).thenReturn(false);

    assertThatThrownBy(() -> authService.changePassword("user-id", "old", "OldPassw0rd!"))
            .isInstanceOf(com.example.commonlib.exception.PasswordPolicyException.class);
  }

  // ---------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------

  private FeignException.Unauthorized unauthorized() {
    Request request =
            Request.create(HttpMethod.POST, "url", Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    return new FeignException.Unauthorized("unauthorized", request, null, Map.of());
  }

  private FeignException serverError() {
    Request request =
            Request.create(HttpMethod.POST, "url", Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    return FeignException.errorStatus(
            "getToken",
            feign.Response.builder()
                    .status(503)
                    .reason("Service Unavailable")
                    .request(request)
                    .headers(Map.of())
                    .build());
  }
}
