package com.example.commonlib.route;

/** Centralized constants for API endpoint paths. */
public final class ApiRoutes {

  private ApiRoutes() {} // Prevent instantiation

  public static final String BASE = "/api/v1";

  public static final class Auth {
    public static final String BASE = ApiRoutes.BASE + "/auth";

    public static final String SIGN_UP_EMAIL = BASE + "/sign-up/email";
    // TS = Trailing Slash
    public static final String SIGN_UP_EMAIL_TS = SIGN_UP_EMAIL + "/";

    public static final String SIGN_UP_EMAIL_VERIFY = BASE + "/sign-up/email/verify";

    public static final String SIGN_UP_EMAIL_VERIFY_TS = SIGN_UP_EMAIL_VERIFY + "/";

    public static final String SIGN_UP_PHONE = BASE + "/sign-up/phone";

    public static final String SIGN_UP_PHONE_TS = SIGN_UP_PHONE + "/";

    public static final String SIGN_UP_PHONE_VERIFY = BASE + "/sign-up/phone/verify";

    public static final String SIGN_UP_PHONE_VERIFY_TS = SIGN_UP_PHONE_VERIFY + "/";

    public static final String SIGN_IN_EMAIL = BASE + "/sign-in/email";

    public static final String SIGN_IN_EMAIL_TS = SIGN_IN_EMAIL + "/";

    public static final String SIGN_IN_PHONE = BASE + "/sign-in/phone";

    public static final String SIGN_IN_PHONE_TS = SIGN_IN_PHONE + "/";

    public static final String SIGN_IN_GOOGLE = BASE + "/oauth/google";

    public static final String SIGN_IN_GOOGLE_TS = SIGN_IN_GOOGLE + "/";

    public static final String AUTH_REFRESH = BASE + "/refresh";

    public static final String AUTH_REFRESH_TS = AUTH_REFRESH + "/";

    public static final String SIGN_OUT = BASE + "/sign-out";

    public static final String SIGN_OUT_TS = SIGN_OUT + "/";

    public static final String FORGET_PASSWORD = BASE + "/forgot-password";

    public static final String FORGET_PASSWORD_TS = FORGET_PASSWORD + "/";

    public static final String CHANGE_PASSWORD = BASE + "/change-password";

    public static final String CHANGE_PASSWORD_TS = CHANGE_PASSWORD + "/";
  }

  public static final class Communication {
    public static final String BASE = ApiRoutes.BASE + "/communication";

    public static final String ADMIN_DQL_EMAIL = BASE + "/admin/dlq/email/replay";
    // TS = Trailing Slash
    public static final String ADMIN_DQL_EMAIL_TS = ADMIN_DQL_EMAIL + "/";

    public static final String ADMIN_DQL_SMS = BASE + "/admin/dlq/sms/replay";
    public static final String ADMIN_DQL_SMS_TS = ADMIN_DQL_SMS + "/";
  }

  public static final class Flight {
    public static final String BASE = ApiRoutes.BASE + "/flights";

    public static final String FLIGHT_SEARCH = BASE + "/search";

    public static final String FLIGHT_SEARCH_TS = FLIGHT_SEARCH + "/";
  }
}
