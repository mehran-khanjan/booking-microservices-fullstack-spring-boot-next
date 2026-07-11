package com.example.communicationservice.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends OTP SMS messages via Twilio.
 *
 * <p>This is the only place in the system that actually calls the Twilio API. It uses Resilience4j
 * retry and circuit‑breaker patterns to handle transient failures and service outages.
 *
 * <p>The Twilio SDK is initialised with the account SID and auth token after bean construction.
 *
 * @see <a href="https://www.twilio.com/docs">Twilio Documentation</a>
 */
@Service
@Slf4j
public class SmsService {

  @Value("${twilio.account-sid}")
  private String accountSid;

  @Value("${twilio.auth-token}")
  private String authToken;

  @Value("${twilio.phone-number}")
  private String fromPhoneNumber;

  /**
   * Initialises the Twilio client with the configured credentials.
   *
   * <p>This method is invoked automatically after the bean is constructed.
   */
  @PostConstruct
  public void init() {
    Twilio.init(accountSid, authToken);
    log.info("✅ Twilio initialized");
  }

  /**
   * Sends an OTP code via SMS to the given phone number.
   *
   * <p>The method is decorated with {@link CircuitBreaker} (name = {@code "twilio"}) and {@link
   * Retry} (name = {@code "twilio"}), so it will retry transient errors and open the circuit if
   * Twilio is consistently unavailable.
   *
   * @param toPhoneNumber the recipient's phone number (in E.164 format)
   * @param otp the one‑time password to send
   * @param expiryMinutes the validity period of the OTP (minutes)
   * @throws IllegalStateException if the SMS fails to send (including circuit‑open fallback)
   */
  @CircuitBreaker(name = "twilio", fallbackMethod = "sendOtpFallback")
  @Retry(name = "twilio")
  public void sendOtp(String toPhoneNumber, String otp, int expiryMinutes) {
    Message message =
        Message.creator(
                new PhoneNumber(toPhoneNumber),
                new PhoneNumber(fromPhoneNumber),
                "Your Booking App verification code is: "
                    + otp
                    + ". Valid for "
                    + expiryMinutes
                    + " minutes.")
            .create();

    log.info("event=sms_sent to={} sid={}", mask(toPhoneNumber), message.getSid());
  }

  /**
   * Fallback method invoked when the circuit is open or retries are exhausted.
   *
   * <p>It rethrows an exception so that the calling layer (e.g., RabbitMQ listener) can nack the
   * message and send it to the DLQ, rather than dropping it silently.
   *
   * @param toPhoneNumber the recipient's phone number
   * @param otp the OTP
   * @param expiryMinutes expiry time
   * @param t the original throwable that triggered the fallback
   * @throws IllegalStateException always thrown to indicate Twilio is unavailable
   */
  private void sendOtpFallback(String toPhoneNumber, String otp, int expiryMinutes, Throwable t) {
    log.error("event=twilio_circuit_open to={}", mask(toPhoneNumber), t);
    throw new IllegalStateException("Twilio temporarily unavailable", t);
  }

  /**
   * Masks a phone number for logging, showing only the last four digits. Example: {@code
   * "+1234567890"} → {@code "****7890"}.
   *
   * @param phone the phone number to mask
   * @return the masked string, or {@code "****"} if the number is shorter than 4 digits
   */
  private String mask(String phone) {
    return phone.length() < 4 ? "****" : "****" + phone.substring(phone.length() - 4);
  }
}
