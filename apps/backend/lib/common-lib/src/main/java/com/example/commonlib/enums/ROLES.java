package com.example.commonlib.enums;

import lombok.Getter;

/** Enumeration of application roles, mapping to Keycloak realm role names. */
@Getter
public enum ROLES {
  USER("ROLE_USER"),
  ADMIN("ROLE_ADMIN");

  private final String keycloakRoleName;

  ROLES(String keycloakRoleName) {
    this.keycloakRoleName = keycloakRoleName;
  }
}
