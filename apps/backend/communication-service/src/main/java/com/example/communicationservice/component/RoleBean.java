package com.example.communicationservice.component;

import com.example.commonlib.enums.ROLES;
import org.springframework.stereotype.Component;

/**
 * Simple bean providing role names as strings, primarily used in Spring Security expressions (e.g.,
 * {@code @PreAuthorize("hasRole(@role.admin())")}).
 *
 * <p>This bean is registered under the name {@code "role"} so it can be referenced in SpEL
 * expressions.
 *
 * @see ROLES
 * @since 1.0.0
 */
@Component("role")
public class RoleBean {

  /**
   * Returns the name of the {@code ADMIN} role as defined in the {@link ROLES} enum.
   *
   * @return the string {@code "ADMIN"}
   */
  public String admin() {
    return ROLES.ADMIN.name(); // Returns "ADMIN"
  }

  /**
   * Returns the name of the {@code USER} role as defined in the {@link ROLES} enum.
   *
   * @return the string {@code "USER"}
   */
  public String user() {
    return ROLES.USER.name(); // Returns "USER"
  }
}
