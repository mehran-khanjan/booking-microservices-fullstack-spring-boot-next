package com.example.commonlib.event;

/**
 * Defines routing keys, exchange names, and queue names used for communication‑related messaging in
 * RabbitMQ.
 *
 * <p>This class centralises all string constants to avoid duplication and typos across the
 * codebase. It includes:
 *
 * <ul>
 *   <li>Main exchange name
 *   <li>Routing keys for OTP email, OTP SMS, and password reset email
 *   <li>Queue names for OTP email and SMS
 *   <li>Dead‑letter exchange and DLQ names for OTP email and SMS
 * </ul>
 *
 * <p><b>Note:</b> This is a utility class with a private constructor – it is not meant to be
 * instantiated.
 *
 * @see <a href="https://www.rabbitmq.com/docs/exchanges">RabbitMQ Exchanges</a>
 * @since 1.0.0
 */
public final class CommunicationRoutingKeys {

  /** Private constructor to prevent instantiation. */
  private CommunicationRoutingKeys() {}

  /** The main topic exchange for communication events. */
  public static final String EXCHANGE = "communication.exchange";

  /** Routing key for OTP email events. */
  public static final String OTP_EMAIL = "communication.otp.email";

  /** Routing key for OTP SMS events. */
  public static final String OTP_SMS = "communication.otp.sms";

  /** Routing key for password reset email events. */
  public static final String PASSWORD_RESET_EMAIL = "communication.password-reset.email";

  /** Queue name for OTP email events. */
  public static final String QUEUE_OTP_EMAIL = "communication.otp.email.queue";

  /** Queue name for OTP SMS events. */
  public static final String QUEUE_OTP_SMS = "communication.otp.sms.queue";

  /** The dead‑letter exchange for communication events. */
  public static final String DLX_EXCHANGE = "communication.dlx";

  /** Dead‑letter queue for OTP email events. */
  public static final String DLQ_OTP_EMAIL = "communication.otp.email.dlq";

  /** Dead‑letter queue for OTP SMS events. */
  public static final String DLQ_OTP_SMS = "communication.otp.sms.dlq";

  public static final String USER_REGISTERED = "communication.user.registered";

  public static final String PASSWORD_RESET = "communication.password.reset";
}
