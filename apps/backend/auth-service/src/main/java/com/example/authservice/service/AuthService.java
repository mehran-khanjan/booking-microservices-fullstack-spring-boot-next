package com.example.authservice.service;

import static com.example.authservice.util.Util.mask;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Orchestrates the user registration flow, delegating to the Keycloak admin service. */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private final KeycloakUserAdminService keycloakUserAdmin;

  /**
   * Registers a new email‑based account (unverified) and dispatches an OTP.
   *
   * @param email the user's email
   * @param password the user's password
   * @return the new Keycloak user ID
   */
  public String registerUser(String email, String password) {
    log.info("Registering user email={}", mask(email));

    String userId =
        keycloakUserAdmin.createUser(
            email,
            null,
            password,
            Map.of("signupMethod", List.of("EMAIL"), "phoneVerified", List.of("false")));

    return userId;
  }
}
