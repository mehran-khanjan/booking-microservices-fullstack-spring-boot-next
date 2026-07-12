package com.example.communicationservice.config;

import com.example.commonlib.event.OtpEmailEvent;
import com.example.commonlib.event.OtpSmsEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Custom {@link MessageConverter} that deserialises incoming RabbitMQ messages based on an {@code
 * eventType} header.
 *
 * <p>The converter expects a header named {@code "eventType"} whose value determines the target
 * class:
 *
 * <ul>
 *   <li>{@code "OTP_EMAIL"} → {@link OtpEmailEvent}
 *   <li>{@code "OTP_SMS"} → {@link OtpSmsEvent}
 * </ul>
 *
 * The message body (JSON) is then deserialised into that class.
 *
 * <p><b>Note:</b> This converter currently only supports {@code fromMessage}. The {@code toMessage}
 * method is not implemented (returns {@code null}).
 *
 * @see OtpEmailEvent
 * @see OtpSmsEvent
 */
public class CustomMessageConverter implements MessageConverter {
  private ObjectMapper objectMapper;

  public CustomMessageConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Not implemented. Returns {@code null}.
   *
   * @param object the object to convert
   * @param messageProperties the message properties
   * @return {@code null}
   * @throws MessageConversionException always (if called)
   */
  @Override
  public Message toMessage(Object object, MessageProperties messageProperties)
      throws MessageConversionException {
    return null;
  }

  /**
   * Converts a RabbitMQ {@link Message} into a Java object.
   *
   * <p>Reads the {@code eventType} header to select the target class, then deserialises the JSON
   * payload.
   *
   * @param message the incoming AMQP message
   * @return the deserialised event object (either {@link OtpEmailEvent} or {@link OtpSmsEvent})
   * @throws MessageConversionException if the header is missing, unknown, or JSON parsing fails
   */
  @Override
  public Object fromMessage(Message message) throws MessageConversionException {
    try {
      // 1. Extract the event type from the headers
      MessageProperties props = message.getMessageProperties();
      String eventType = props.getHeader("eventType"); // e.g., "OTP_EMAIL" or "OTP_SMS"

      if (eventType == null) {
        throw new MessageConversionException("Missing 'eventType' header");
      }

      // 2. Map header value to the corresponding Class
      Class<?> targetClass;
      switch (eventType) {
        case "OTP_EMAIL":
          targetClass = OtpEmailEvent.class;
          break;
        case "OTP_SMS":
          targetClass = OtpSmsEvent.class;
          break;
        default:
          throw new MessageConversionException("Unknown eventType: " + eventType);
      }

      // 3. Deserialize using the resolved class
      return this.objectMapper.readValue(message.getBody(), targetClass);
    } catch (Exception e) {
      throw new MessageConversionException("Failed to convert JSON to object", e);
    }
  }
}
