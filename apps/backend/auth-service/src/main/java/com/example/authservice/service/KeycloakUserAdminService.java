package com.example.authservice.service;

import static com.example.authservice.util.Util.mask;

import com.example.commonlib.enums.ROLES;
import com.example.commonlib.exception.KeycloakOperationException;
import com.example.commonlib.exception.UserAlreadyExistsException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Thin, resilient adapter over the Keycloak Admin REST API.
 *
 * <p>All external I/O to Keycloak's admin endpoints lives here, with circuit breaker, retry, and
 * bulkhead patterns applied.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserAdminService {

  private final Keycloak keycloakAdmin;

  @Value("${keycloak.realm}")
  private String realm;

  private RealmResource realm() {
    return keycloakAdmin.realm(realm);
  }

  /**
   * Creates a Keycloak user with the given credentials/attributes and assigns ROLE_USER. Attributes
   * must already include {@code signupMethod}, {@code phoneVerified}.
   *
   * @param email user email (may be null if phone is used)
   * @param phone user phone (may be null if email is used)
   * @param password user password
   * @param attributes additional user attributes
   * @return the Keycloak user ID
   * @throws UserAlreadyExistsException if the user already exists
   * @throws KeycloakOperationException if the operation fails
   */
  @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "createUserFallback")
  @Retry(name = "keycloakAdmin")
  @Bulkhead(name = "keycloakAdmin")
  public String createUser(
      String email, String phone, String password, Map<String, List<String>> attributes) {
    UserRepresentation user = new UserRepresentation();
    if (email != null) {
      user.setUsername(email);
      user.setEmail(email);
    } else {
      user.setUsername(phone);
    }
    user.setEnabled(
        true); // always true — verification tracked via custom attributes, never via `enabled`
    user.setFirstName("User");
    user.setLastName("User");

    Map<String, List<String>> attrs = new HashMap<>(attributes);
    if (phone != null) attrs.put("phoneNumber", List.of(phone));
    user.setAttributes(attrs);

    CredentialRepresentation credential = new CredentialRepresentation();
    credential.setType(CredentialRepresentation.PASSWORD);
    credential.setValue(password);
    credential.setTemporary(false);
    user.setCredentials(List.of(credential));

    try (Response response = realm().users().create(user)) {
      if (response.getStatus() == 409) {
        throw new UserAlreadyExistsException("User already exists");
      }
      if (response.getStatus() != 201) {
        String body = response.readEntity(String.class);
        log.error("event=kc_create_user_failed status={} body={}", response.getStatus(), body);
        throw new KeycloakOperationException("User creation failed: " + body);
      }
      String userId = extractIdFromLocation(response);
      assignRealmRole(userId, ROLES.USER.getKeycloakRoleName());
      log.info("event=kc_user_created userId={}", userId);
      return userId;
    }
  }

  private String createUserFallback(
      String email,
      String phone,
      String password,
      Map<String, List<String>> attributes,
      Throwable t) {
    if (t instanceof UserAlreadyExistsException) {
      log.warn("event=kc_user_exists email={}", mask(email));
      throw (UserAlreadyExistsException) t;
    }
    log.error("event=kc_circuit_open op=createUser", t);
    throw new KeycloakOperationException("Auth service temporarily unavailable");
  }

  private String extractIdFromLocation(Response response) {
    String location = response.getHeaderString("Location");
    if (location == null) throw new KeycloakOperationException("Failed to extract created user id");
    String[] parts = location.split("/");
    return parts[parts.length - 1];
  }

  private void assignRealmRole(String userId, String roleName) {
    UserResource userResource = realm().users().get(userId);
    RoleRepresentation role = realm().roles().get(roleName).toRepresentation();
    userResource.roles().realmLevel().add(List.of(role));
  }
}
