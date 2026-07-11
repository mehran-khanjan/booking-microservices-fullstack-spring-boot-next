package com.example.communicationservice.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails via SendGrid.
 *
 * <p>This is the only place in the system that actually calls SendGrid. It uses Resilience4j retry
 * and circuit‑breaker patterns to handle transient failures and service outages.
 */
@Service
@Slf4j
public class EmailService {

  @Value("${sendgrid.api-key}")
  private String apiKey;

  @Value("${sendgrid.from-email}")
  private String fromEmail;

  @Value("${sendgrid.from-name}")
  private String fromName;

  /**
   * Sends an OTP code email to the recipient.
   *
   * <p>The method is decorated with {@link CircuitBreaker} (name = {@code "sendgrid"}) and {@link
   * Retry} (name = {@code "sendgrid"}), so it will retry transient errors and open the circuit if
   * SendGrid is consistently unavailable.
   *
   * @param toEmail the recipient's email address
   * @param otp the one‑time password to send
   * @param expiryMinutes the validity period of the OTP (minutes)
   * @throws IllegalStateException if the email fails to send (including circuit‑open fallback)
   */
  @CircuitBreaker(name = "sendgrid", fallbackMethod = "sendOtpEmailFallback")
  @Retry(name = "sendgrid")
  public void sendOtpEmail(String toEmail, String otp, int expiryMinutes) {
    Content content =
        new Content(
            "text/html",
            "<h2>Your Booking App verification code</h2>"
                + "<p style=\"font-size:24px;font-weight:bold;\">"
                + otp
                + "</p>"
                + "<p>This code expires in "
                + expiryMinutes
                + " minutes.</p>");
    send(toEmail, "Verify Your Email - Booking App", content);
  }

  /**
   * Sends a password reset email with a link.
   *
   * @param toEmail the recipient's email address
   * @param resetLink the password reset URL
   */
  public void sendPasswordResetEmail(String toEmail, String resetLink) {
    Content content =
        new Content(
            "text/html",
            "<h2>Password Reset</h2>"
                + "<p>Click the link below to reset your password:</p>"
                + "<a href=\""
                + resetLink
                + "\">Reset Password</a>"
                + "<p>This link expires in 1 hour. If you didn't request this, ignore this email.</p>");
    send(toEmail, "Password Reset Request - Booking App", content);
  }

  /**
   * Internal method that performs the actual SendGrid API call.
   *
   * <p>The email address is masked in logs for privacy.
   *
   * @param toEmail the recipient
   * @param subject the email subject
   * @param content the email body (HTML)
   * @throws IllegalStateException if the API call fails or returns a non‑2xx status
   */
  private void send(String toEmail, String subject, Content content) {
    Email from = new Email(fromEmail, fromName);
    Email to = new Email(toEmail);
    Mail mail = new Mail(from, subject, to, content);
    SendGrid sg = new SendGrid(apiKey);

    Request request = new Request();
    try {
      request.setMethod(Method.POST);
      request.setEndpoint("mail/send");
      request.setBody(mail.build());
      Response response = sg.api(request);

      if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
        log.error(
            "event=sendgrid_error status={} body={}", response.getStatusCode(), response.getBody());
        throw new IllegalStateException("SendGrid returned status " + response.getStatusCode());
      }
      log.info("event=email_sent to={}", mask(toEmail));
    } catch (IOException e) {
      log.error("event=sendgrid_io_error to={}", mask(toEmail), e);
      throw new IllegalStateException("Failed to send email via SendGrid", e);
    }
  }

  /**
   * Fallback method invoked when the circuit is open or retries are exhausted.
   *
   * <p>It rethrows an exception so that the calling layer (e.g., RabbitMQ listener) can nack the
   * message and send it to the DLQ, rather than dropping it silently.
   *
   * @param toEmail the recipient
   * @param otp the OTP
   * @param expiryMinutes expiry time
   * @param t the original throwable that triggered the fallback
   * @throws IllegalStateException always thrown to indicate SendGrid is unavailable
   */
  private void sendOtpEmailFallback(String toEmail, String otp, int expiryMinutes, Throwable t) {
    log.error("event=sendgrid_circuit_open to={}", mask(toEmail), t);
    throw new IllegalStateException("SendGrid temporarily unavailable", t);
  }

  /**
   * Masks an email address for logging, showing only the first character and the domain. Example:
   * {@code "john.doe@example.com"} → {@code "j***@example.com"}.
   *
   * @param email the email address to mask
   * @return the masked string, or {@code "***@***"} if the address is short
   */
  private String mask(String email) {
    int at = email.indexOf('@');
    return at <= 1 ? "***@***" : email.charAt(0) + "***" + email.substring(at);
  }
}
