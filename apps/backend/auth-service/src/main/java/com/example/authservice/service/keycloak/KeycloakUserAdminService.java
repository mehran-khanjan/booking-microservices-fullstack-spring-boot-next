package com.example.authservice.service.keycloak;

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
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
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
    log.error("event=kc_circuit_open op=createUser", t);

    if (t instanceof UserAlreadyExistsException) {
      log.warn("event=kc_user_exists email={}", mask(email));
      throw (UserAlreadyExistsException) t;
    }
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    throw new KeycloakOperationException("Auth service temporarily unavailable");
  }

  private String extractIdFromLocation(Response response) {
    String location = response.getHeaderString("Location");
    if (location == null) throw new KeycloakOperationException("Failed to extract created user id");
    String[] parts = location.split("/");
    return parts[parts.length - 1];
  }

  @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "userExistsFallback")
  @Retry(name = "keycloakAdmin")
  @Bulkhead(name = "keycloakAdmin")
  public boolean userExists(String identifier) {
    return !realm().users().search(identifier, true).isEmpty();
  }

  private boolean userExistsFallback(String identifier, Throwable t) {
    log.error("event=kc_circuit_open op=userExists", t);
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    throw new KeycloakOperationException("Auth service temporarily unavailable");
  }

  @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "updateEmailVerifiedFallback")
  @Retry(name = "keycloakAdmin")
  public void updateEmailVerified(String userId, boolean verified) {
    UserResource userResource = realm().users().get(userId);
    UserRepresentation user = userResource.toRepresentation();
    user.setEmailVerified(verified);
    userResource.update(user);
    log.info("event=kc_email_verified_updated userId={} verified={}", userId, verified);
  }

  private void updateEmailVerifiedFallback(String userId, boolean verified, Throwable t) {
    log.error("event=kc_circuit_open op=updateEmailVerified", t);
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    throw new KeycloakOperationException("Auth service temporarily unavailable");
  }

  @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "setUserAttributeFallback")
  @Retry(name = "keycloakAdmin")
  public void setUserAttribute(String userId, String key, String value) {
    UserResource userResource = realm().users().get(userId);
    UserRepresentation user = userResource.toRepresentation();
    Map<String, List<String>> attrs =
        user.getAttributes() != null ? user.getAttributes() : new HashMap<>();
    attrs.put(key, List.of(value));
    user.setAttributes(attrs);
    userResource.update(user);
    log.info("event=kc_attribute_updated userId={} key={}", userId, key);
  }

  private void setUserAttributeFallback(String userId, String key, String value, Throwable t) {
    log.error("event=kc_circuit_open op=setUserAttribute", t);
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    throw new KeycloakOperationException("Auth service temporarily unavailable");
  }

  @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "findByEmailFallback")
  @Retry(name = "keycloakAdmin")
  public List<UserRepresentation> findByEmail(String email) {
    return realm().users().searchByEmail(email, true);
  }

  private List<UserRepresentation> findByEmailFallback(String email, Throwable t) {
    log.error("event=kc_circuit_open op=findByEmail", t);
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    throw new KeycloakOperationException("Auth service temporarily unavailable");
  }

  @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "createGoogleUserFallback")
  @Retry(name = "keycloakAdmin")
  public UserRepresentation createGoogleUser(
      String email, String firstName, String lastName, String googleSub) {
    UserRepresentation user = new UserRepresentation();
    user.setUsername(email);
    user.setEmail(email);
    user.setEmailVerified(true);
    user.setEnabled(true);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setAttributes(
        Map.of(
            "signupMethod", List.of("GOOGLE"),
            "phoneVerified", List.of("false")));

    UsersResource users = realm().users();
    String userId;
    try (Response resp = users.create(user)) {
      if (resp.getStatus() != 201) {
        log.error("event=kc_create_google_user_failed status={}", resp.getStatus());
        throw new KeycloakOperationException("Failed to create Keycloak user for Google signup");
      }
      userId = extractIdFromLocation(resp);
    }
    user.setId(userId);
    linkFederatedIdentity(userId, googleSub, email);
    assignRealmRole(userId, ROLES.USER.getKeycloakRoleName());
    return user;
  }

  private UserRepresentation createGoogleUserFallback(
      String email, String fn, String ln, String sub, Throwable t) {
    log.error("event=kc_circuit_open op=createGoogleUser", t);
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    throw new KeycloakOperationException("Auth service temporarily unavailable");
  }

  @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "linkFederatedIdentityFallback")
  @Retry(name = "keycloakAdmin")
  public void linkFederatedIdentity(String userId, String googleSub, String email) {
    FederatedIdentityRepresentation fed = new FederatedIdentityRepresentation();
    fed.setIdentityProvider("google");
    fed.setUserId(googleSub);
    fed.setUserName(email);
    try (Response resp = realm().users().get(userId).addFederatedIdentity("google", fed)) {
      if (resp.getStatus() >= 300) {
        throw new KeycloakOperationException("Failed to link Google identity");
      }
    }
  }

  private void linkFederatedIdentityFallback(String userId, String sub, String email, Throwable t) {
    log.error("event=kc_circuit_open op=linkFederatedIdentity", t);
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    throw new KeycloakOperationException("Auth service temporarily unavailable");
  }

  public boolean hasFederatedIdentity(String userId, String provider) {
    return realm().users().get(userId).getFederatedIdentity().stream()
        .anyMatch(fi -> provider.equals(fi.getIdentityProvider()));
  }

  private void assignRealmRole(String userId, String roleName) {
    UserResource userResource = realm().users().get(userId);
    RoleRepresentation role = realm().roles().get(roleName).toRepresentation();
    userResource.roles().realmLevel().add(List.of(role));
  }

  // Add this method to KeycloakUserAdminService
  @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "findByUsernameFallback")
  @Retry(name = "keycloakAdmin")
  public UserRepresentation findByUsername(String username) {
    List<UserRepresentation> users = realm().users().search(username, true);
    if (users.isEmpty()) {
      throw new KeycloakOperationException("User not found: " + username);
    }
    return users.get(0);
  }

  private UserRepresentation findByUsernameFallback(String username, Throwable t) {
    log.error("event=kc_circuit_open op=findByUsername", t);
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    throw new KeycloakOperationException("Auth service temporarily unavailable");
  }
}
