package com.example.commonlib.route;

/** Centralized constants for API endpoint paths. */
public final class ApiRoutes {

  private ApiRoutes() {} // Prevent instantiation

  public static final String BASE = "/api/v1";

  public static final class Auth {
    public static final String BASE = ApiRoutes.BASE + "/auth";

    public static final String SIGN_UP = BASE + "/sign-up/email";
    // TS = Trailing Slash
    public static final String SIGN_UP_TS = SIGN_UP + "/";
  }
}
