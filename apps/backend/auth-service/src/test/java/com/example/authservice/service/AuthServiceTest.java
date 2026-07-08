package com.example.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private KeycloakUserAdminService keycloakUserAdminService;

  @Captor private ArgumentCaptor<Map<String, List<String>>> attributesCaptor;

  private AuthService authService;

  @Test
  void registerUser_delegatesToKeycloakAdminService_andReturnsUserId() {
    authService = new AuthService(keycloakUserAdminService);
    when(keycloakUserAdminService.createUser(
            eq("user@example.com"), isNull(), eq("Passw0rd!"), anyMap()))
        .thenReturn("generated-user-id");

    String userId = authService.registerUser("user@example.com", "Passw0rd!");

    assertThat(userId).isEqualTo("generated-user-id");
  }

  @Test
  void registerUser_setsSignupMethodAndPhoneVerifiedAttributes() {
    authService = new AuthService(keycloakUserAdminService);
    when(keycloakUserAdminService.createUser(any(), any(), any(), attributesCaptor.capture()))
        .thenReturn("user-id");

    authService.registerUser("user@example.com", "Passw0rd!");

    Map<String, List<String>> attributes = attributesCaptor.getValue();
    assertThat(attributes).containsEntry("signupMethod", List.of("EMAIL"));
    assertThat(attributes).containsEntry("phoneVerified", List.of("false"));
  }

  @Test
  void registerUser_passesNullPhone() {
    authService = new AuthService(keycloakUserAdminService);
    when(keycloakUserAdminService.createUser(any(), isNull(), any(), anyMap())).thenReturn("id");

    authService.registerUser("user@example.com", "pw");

    verify(keycloakUserAdminService)
        .createUser(eq("user@example.com"), isNull(), eq("pw"), anyMap());
  }
}
