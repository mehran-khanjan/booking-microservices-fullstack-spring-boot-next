package com.example.communicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class SmsServiceTest {

  private SmsService newService() {
    SmsService service = new SmsService();
    ReflectionTestUtils.setField(service, "accountSid", "ACtest");
    ReflectionTestUtils.setField(service, "authToken", "token");
    ReflectionTestUtils.setField(service, "fromPhoneNumber", "+15005550006");
    return service;
  }

  @Test
  void init_initializesTwilioSdkWithConfiguredCredentials() {
    try (MockedStatic<Twilio> twilioMock = Mockito.mockStatic(Twilio.class)) {
      SmsService service = newService();

      service.init();

      twilioMock.verify(() -> Twilio.init("ACtest", "token"));
    }
  }

  @Test
  void sendOtp_success_createsMessageThroughTwilioClient() {
    try (MockedStatic<Message> messageMock = Mockito.mockStatic(Message.class)) {
      MessageCreator creator = mock(MessageCreator.class);
      Message createdMessage = mock(Message.class);
      when(createdMessage.getSid()).thenReturn("SM123");
      when(creator.create()).thenReturn(createdMessage);
      messageMock
          .when(() -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()))
          .thenReturn(creator);
      SmsService service = newService();

      service.sendOtp("+15005550001", "123456", 5);

      messageMock.verify(
          () -> Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString()));
    }
  }

  @Test
  void sendOtpFallback_invokedDirectly_logsAndRethrows() {
    SmsService service = newService();
    RuntimeException cause = new RuntimeException("circuit open");

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    service, "sendOtpFallback", "+15005550001", "123456", 5, cause))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Twilio temporarily unavailable");
  }

  @Test
  void mask_shortPhoneNumber_returnsFullyMaskedValue() {
    SmsService service = newService();

    String masked = ReflectionTestUtils.invokeMethod(service, "mask", "123");

    assertThat(masked).isEqualTo("****");
  }

  @Test
  void mask_normalPhoneNumber_keepsLastFourDigits() {
    SmsService service = newService();

    String masked = ReflectionTestUtils.invokeMethod(service, "mask", "+15005550006");

    assertThat(masked).isEqualTo("****0006");
  }
}
