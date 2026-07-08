package com.example.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commonlib.exception.KeycloakOperationException;
import com.example.commonlib.exception.UserAlreadyExistsException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KeycloakUserAdminServiceTest {

  private static final String REALM = "booking-app-realm";

  @Mock private Keycloak keycloakAdmin;
  @Mock private RealmResource realmResource;
  @Mock private UsersResource usersResource;
  @Mock private UserResource userResource;
  @Mock private RolesResource rolesResource;
  @Mock private RoleResource roleResource;
  @Mock private RoleMappingResource roleMappingResource;
  @Mock private RoleScopeResource roleScopeResource;
  @Mock private Response response;

  private KeycloakUserAdminService service;

  @BeforeEach
  void setUp() {
    service = new KeycloakUserAdminService(keycloakAdmin);
    ReflectionTestUtils.setField(service, "realm", REALM);
  }

  @Test
  void createUser_success_extractsIdAndAssignsUserRole() {
    when(keycloakAdmin.realm(REALM)).thenReturn(realmResource);
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(201);
    when(response.getHeaderString("Location"))
        .thenReturn("http://keycloak/admin/realms/booking-app-realm/users/abc-123");
    when(usersResource.get("abc-123")).thenReturn(userResource);
    when(userResource.roles()).thenReturn(roleMappingResource);
    when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
    when(realmResource.roles()).thenReturn(rolesResource);
    when(rolesResource.get("ROLE_USER")).thenReturn(roleResource);
    when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation());

    String userId =
        service.createUser(
            "user@example.com",
            null,
            "Passw0rd!",
            Map.of("signupMethod", List.of("EMAIL"), "phoneVerified", List.of("false")));

    assertThat(userId).isEqualTo("abc-123");
    verify(roleScopeResource).add(anyList());
    verify(response).close();
  }

  @Test
  void createUser_withPhone_setsUsernameFromPhoneAndAddsPhoneAttribute() {
    when(keycloakAdmin.realm(REALM)).thenReturn(realmResource);
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(201);
    when(response.getHeaderString("Location")).thenReturn("http://keycloak/x/users/xyz-999");
    when(usersResource.get("xyz-999")).thenReturn(userResource);
    when(userResource.roles()).thenReturn(roleMappingResource);
    when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
    when(realmResource.roles()).thenReturn(rolesResource);
    when(rolesResource.get("ROLE_USER")).thenReturn(roleResource);
    when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation());

    String userId = service.createUser(null, "+15551234567", "Passw0rd!", Map.of());

    assertThat(userId).isEqualTo("xyz-999");
  }

  @Test
  void createUser_conflictStatus_throwsUserAlreadyExistsException() {
    when(keycloakAdmin.realm(REALM)).thenReturn(realmResource);
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(409);

    assertThatThrownBy(() -> service.createUser("dup@example.com", null, "pw", Map.of()))
        .isInstanceOf(UserAlreadyExistsException.class);

    verify(response).close();
  }

  @Test
  void createUser_unexpectedStatus_throwsKeycloakOperationException() {
    when(keycloakAdmin.realm(REALM)).thenReturn(realmResource);
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(500);
    when(response.readEntity(String.class)).thenReturn("internal error");

    assertThatThrownBy(() -> service.createUser("user@example.com", null, "pw", Map.of()))
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("User creation failed");
  }

  @Test
  void createUser_missingLocationHeader_throwsKeycloakOperationException() {
    when(keycloakAdmin.realm(REALM)).thenReturn(realmResource);
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(201);
    when(response.getHeaderString("Location")).thenReturn(null);

    assertThatThrownBy(() -> service.createUser("user@example.com", null, "pw", Map.of()))
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("Failed to extract created user id");
  }

  @Test
  void createUserFallback_rethrowsUserAlreadyExistsException() {
    UserAlreadyExistsException original = new UserAlreadyExistsException("exists");

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    service,
                    "createUserFallback",
                    "user@example.com",
                    null,
                    "pw",
                    Map.of(),
                    original))
        .isInstanceOf(UserAlreadyExistsException.class);
  }

  @Test
  void createUserFallback_wrapsOtherThrowablesAsKeycloakOperationException() {
    RuntimeException circuitOpen = new RuntimeException("circuit open");

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    service,
                    "createUserFallback",
                    "user@example.com",
                    null,
                    "pw",
                    Map.of(),
                    circuitOpen))
        .isInstanceOf(KeycloakOperationException.class)
        .hasMessageContaining("temporarily unavailable");
  }

  @Test
  void createUser_neverAssignsRoleWhenCreationFails() {
    when(keycloakAdmin.realm(REALM)).thenReturn(realmResource);
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(409);

    assertThatThrownBy(() -> service.createUser("dup@example.com", null, "pw", Map.of()))
        .isInstanceOf(UserAlreadyExistsException.class);

    verify(usersResource, never()).get(any());
    verify(usersResource, times(1)).create(any(UserRepresentation.class));
  }
}
