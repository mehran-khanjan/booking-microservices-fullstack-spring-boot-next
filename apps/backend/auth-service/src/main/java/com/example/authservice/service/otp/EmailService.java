package com.example.authservice.service.otp;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.commonlib.event.OtpEmailEvent;
import com.example.outboxlib.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for queuing email OTP delivery intents via the transactional outbox pattern.
 *
 * <p>This service does not send emails directly; instead, it creates an {@link OtpEmailEvent} and
 * stores it in the outbox table using {@link OutboxService}. The actual email delivery is handled
 * asynchronously by the {@code notification-service}, which consumes messages from the {@link
 * CommunicationRoutingKeys#OTP_EMAIL} queue. This approach ensures that email dispatch is reliable
 * and transactional with the business operation that triggered it.
 *
 * <p>The service is called from within an existing transaction (typically from {@link OtpService})
 * to guarantee that the outbox record is committed together with the OTP storage.
 *
 * @author Your Team
 * @see OtpEmailEvent
 * @see OutboxService
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

  /** Service for persisting events to the outbox for reliable asynchronous processing. */
  private final OutboxService outboxService;

  /** OTP expiry duration in minutes, injected from application properties (default: 5). */
  @Value("${otp.expiry-minutes:5}")
  private int otpExpiryMinutes;

  /**
   * Queues an OTP email event for a given recipient.
   *
   * <p>This method constructs an {@link OtpEmailEvent} containing the recipient's email, the OTP
   * code, and the expiry duration. It then stores the event in the outbox using {@link
   * OutboxService#saveEvent(Object, String, String, String, String, String)}. The outbox record is
   * associated with the routing key {@code CommunicationRoutingKeys.OTP_EMAIL} and the exchange
   * {@code CommunicationRoutingKeys.EXCHANGE}.
   *
   * <p><strong>Important:</strong> This method must be invoked from within an active transaction
   * (e.g., a transactional service method) to ensure that the outbox record is only persisted if
   * the surrounding business operation succeeds.
   *
   * <p>The email address is masked in logs for privacy protection.
   *
   * @param toEmail the recipient's email address (must not be {@code null} or empty)
   * @param otp the one-time password to be sent (must not be {@code null} or empty)
   * @throws IllegalArgumentException if the outbox service fails to save the event
   */
  public void sendOtpEmail(String toEmail, String otp) {
    OtpEmailEvent event = new OtpEmailEvent(toEmail, otp, otpExpiryMinutes);

    outboxService.saveEvent(
        event,
        "OTP_EMAIL",
        CommunicationRoutingKeys.EXCHANGE,
        CommunicationRoutingKeys.OTP_EMAIL,
        "User",
        toEmail);

    log.info("event=otp_email_queued to={}", mask(toEmail));
  }

  /**
   * Masks an email address for logging purposes to protect user privacy.
   *
   * <p>If the email contains an '@' symbol, the part before the '@' is shortened to the first
   * character followed by "***" (e.g., "j***@example.com"). If the local part is too short (1
   * character or less), a generic mask is returned.
   *
   * @param email the email address to mask (may be {@code null} or empty)
   * @return the masked email string, or "***@***" if the input cannot be masked properly
   */
  private String mask(String email) {
    int at = email.indexOf('@');
    return at <= 1 ? "***@***" : email.charAt(0) + "***" + email.substring(at);
  }
}
