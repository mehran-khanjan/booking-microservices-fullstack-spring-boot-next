package com.example.communicationservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commonlib.event.OtpEmailEvent;
import com.example.commonlib.event.OtpSmsEvent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class CustomMessageConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CustomMessageConverter converter = new CustomMessageConverter(objectMapper);

  @Test
  void toMessage_alwaysReturnsNull() {
    assertThat(converter.toMessage("payload", new MessageProperties())).isNull();
  }

  @Test
  void fromMessage_otpEmailEventType_deserializesToOtpEmailEvent() throws JsonProcessingException {
    String json = objectMapper.writeValueAsString(new OtpEmailEvent("user@example.com", "123456", 5));
    Message message = buildMessage(json, "OTP_EMAIL");

    Object result = converter.fromMessage(message);

    assertThat(result).isInstanceOf(OtpEmailEvent.class);
    assertThat(((OtpEmailEvent) result).toEmail()).isEqualTo("user@example.com");
  }

  @Test
  void fromMessage_otpSmsEventType_deserializesToOtpSmsEvent() throws JsonProcessingException {
    String json = objectMapper.writeValueAsString(new OtpSmsEvent("+15005550006", "654321", 5));
    Message message = buildMessage(json, "OTP_SMS");

    Object result = converter.fromMessage(message);

    assertThat(result).isInstanceOf(OtpSmsEvent.class);
    assertThat(((OtpSmsEvent) result).toPhoneNumber()).isEqualTo("+15005550006");
  }

  @Test
  void fromMessage_missingEventTypeHeader_throwsMessageConversionException() {
    Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());

    assertThatThrownBy(() -> converter.fromMessage(message))
        .isInstanceOf(MessageConversionException.class)
        .hasMessageContaining("Failed to convert JSON to object");
  }

  @Test
  void fromMessage_unknownEventType_throwsMessageConversionException() {
    Message message = buildMessage("{}", "UNKNOWN_TYPE");

    assertThatThrownBy(() -> converter.fromMessage(message))
        .isInstanceOf(MessageConversionException.class)
        .hasMessageContaining("Failed to convert JSON to object");
  }

  @Test
  void fromMessage_malformedJson_throwsMessageConversionException() {
    Message message = buildMessage("not-json", "OTP_EMAIL");

    assertThatThrownBy(() -> converter.fromMessage(message))
        .isInstanceOf(MessageConversionException.class)
        .hasMessageContaining("Failed to convert JSON to object");
  }

  private Message buildMessage(String jsonBody, String eventType) {
    MessageProperties props = new MessageProperties();
    if (eventType != null) {
      props.setHeader("eventType", eventType);
    }
    return new Message(jsonBody.getBytes(StandardCharsets.UTF_8), props);
  }
}
