package com.example.authservice.service.otp;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.commonlib.event.OtpSmsEvent;
import com.example.outboxlib.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for queuing SMS OTP delivery intents via the transactional outbox.
 *
 * <p>This service does not send SMS messages directly; instead, it creates an {@link OtpSmsEvent}
 * and stores it in the outbox table using {@link OutboxService}. The actual SMS delivery is handled
 * asynchronously by the {@code notification-service}, which consumes messages from the {@link
 * CommunicationRoutingKeys#OTP_SMS} queue.
 *
 * <p>This decouples the authentication service from the SMS provider (Twilio) and ensures that SMS
 * dispatch is reliable and transactional with the business operation that triggered it.
 *
 * <p>The service is typically called from within an existing transaction (e.g., from {@link
 * OtpService}) to guarantee that the outbox record is committed together with the OTP storage.
 *
 * @author Your Team
 * @see OtpSmsEvent
 * @see OutboxService
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmsService {

  /** Service for persisting events to the outbox for reliable asynchronous processing. */
  private final OutboxService outboxService;

  /** OTP expiry duration in minutes, injected from application properties (default: 5). */
  @Value("${otp.expiry-minutes:5}")
  private int otpExpiryMinutes;

  /**
   * Queues an OTP SMS event for a given phone number.
   *
   * <p>This method constructs an {@link OtpSmsEvent} containing the recipient's phone number, the
   * OTP code, and the expiry duration. It then stores the event in the outbox using {@link
   * OutboxService#saveEvent(Object, String, String, String, String, String)}. The outbox record is
   * associated with the routing key {@code CommunicationRoutingKeys.OTP_SMS} and the exchange
   * {@code CommunicationRoutingKeys.EXCHANGE}.
   *
   * <p><strong>Important:</strong> This method must be invoked from within an active transaction
   * (e.g., a transactional service method) to ensure that the outbox record is only persisted if
   * the surrounding business operation succeeds.
   *
   * <p>The phone number is masked in logs for privacy protection, showing only the last 4 digits.
   *
   * @param toPhoneNumber the recipient's phone number in E.164 format (must not be {@code null} or
   *     empty)
   * @param otp the one-time password to be sent (must not be {@code null} or empty)
   * @throws IllegalArgumentException if the outbox service fails to save the event
   */
  public void sendOtp(String toPhoneNumber, String otp) {
    OtpSmsEvent event = new OtpSmsEvent(toPhoneNumber, otp, otpExpiryMinutes);
    outboxService.saveEvent(
        event,
        "OTP_SMS",
        CommunicationRoutingKeys.EXCHANGE,
        CommunicationRoutingKeys.OTP_SMS,
        "User",
        toPhoneNumber);
    log.info("event=otp_sms_queued to={}", mask(toPhoneNumber));
  }

  /**
   * Masks a phone number for logging purposes to protect user privacy.
   *
   * <p>If the number is shorter than 4 digits, it is replaced with "****". Otherwise, it shows
   * "****" followed by the last 4 digits.
   *
   * @param phone the phone number to mask (may be {@code null} or empty)
   * @return the masked phone string
   */
  private String mask(String phone) {
    return phone.length() < 4 ? "****" : "****" + phone.substring(phone.length() - 4);
  }
}
