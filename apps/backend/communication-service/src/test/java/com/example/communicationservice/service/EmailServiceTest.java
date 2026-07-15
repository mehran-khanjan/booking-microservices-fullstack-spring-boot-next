package com.example.communicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link EmailService} builds its {@link SendGrid} client with {@code new SendGrid(apiKey)}
 * inline, so instead of refactoring production code to inject it we intercept construction with
 * Mockito's {@link Mockito#mockConstruction} - every {@code new SendGrid(...)} call inside the
 * class under test returns a controllable mock for the duration of the try-with-resources block.
 */
class EmailServiceTest {

  private EmailService newService() {
    EmailService service = new EmailService();
    ReflectionTestUtils.setField(service, "apiKey", "test-key");
    ReflectionTestUtils.setField(service, "fromEmail", "no-reply@booking-app.com");
    ReflectionTestUtils.setField(service, "fromName", "Booking App");
    return service;
  }

  @Test
  void sendOtpEmail_successfulResponse_doesNotThrow() throws IOException {
    try (MockedConstruction<SendGrid> mocked =
        Mockito.mockConstruction(
            SendGrid.class,
            (mock, context) -> {
              Response response = new Response(202, "{}", null);
              when(mock.api(any(Request.class))).thenReturn(response);
            })) {
      EmailService service = newService();

      service.sendOtpEmail("user@example.com", "123456", 5);

      assertThat(mocked.constructed()).hasSize(1);
    }
  }

  @Test
  void sendPasswordResetEmail_successfulResponse_doesNotThrow() throws IOException {
    try (MockedConstruction<SendGrid> mocked =
        Mockito.mockConstruction(
            SendGrid.class,
            (mock, context) -> {
              Response response = new Response(200, "{}", null);
              when(mock.api(any(Request.class))).thenReturn(response);
            })) {
      EmailService service = newService();

      service.sendPasswordResetEmail("user@example.com", "https://booking-app.com/reset?token=abc");

      assertThat(mocked.constructed()).hasSize(1);
    }
  }

  @Test
  void sendOtpEmail_sendGridReturnsErrorStatus_throwsIllegalStateException() throws IOException {
    try (MockedConstruction<SendGrid> mocked =
        Mockito.mockConstruction(
            SendGrid.class,
            (mock, context) -> {
              Response response = new Response(500, "server error", null);
              when(mock.api(any(Request.class))).thenReturn(response);
            })) {
      EmailService service = newService();

      assertThatThrownBy(() -> service.sendOtpEmail("user@example.com", "123456", 5))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SendGrid returned status");
    }
  }

  @Test
  void sendOtpEmail_sendGridThrowsIOException_wrapsInIllegalStateException() throws IOException {
    try (MockedConstruction<SendGrid> mocked =
        Mockito.mockConstruction(
            SendGrid.class,
            (mock, context) -> when(mock.api(any(Request.class))).thenThrow(new IOException("network down")))) {
      EmailService service = newService();

      assertThatThrownBy(() -> service.sendOtpEmail("user@example.com", "123456", 5))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to send email via SendGrid");
    }
  }

  @Test
  void sendOtpEmailFallback_invokedDirectly_logsAndRethrows() {
    EmailService service = newService();
    RuntimeException cause = new RuntimeException("circuit open");

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    service, "sendOtpEmailFallback", "user@example.com", "123456", 5, cause))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SendGrid temporarily unavailable");
  }

  @Test
  void mask_singleCharLocalPart_returnsFullyMaskedAddress() {
    EmailService service = newService();

    String masked = ReflectionTestUtils.invokeMethod(service, "mask", "a@example.com");

    assertThat(masked).isEqualTo("***@***");
  }

  @Test
  void mask_twoCharLocalPart_keepsFirstCharAndDomain() {
    EmailService service = newService();

    String masked = ReflectionTestUtils.invokeMethod(service, "mask", "ab@example.com");

    assertThat(masked).isEqualTo("a***@example.com");
  }

  @Test
  void mask_normalEmail_keepsFirstCharAndDomain() {
    EmailService service = newService();

    String masked = ReflectionTestUtils.invokeMethod(service, "mask", "johndoe@example.com");

    assertThat(masked).isEqualTo("j***@example.com");
  }
}
