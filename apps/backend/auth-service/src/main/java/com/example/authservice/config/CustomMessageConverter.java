package com.example.authservice.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.ObjectMapper;

/**
 * Custom implementation of Spring AMQP's {@link MessageConverter} that uses Jackson {@link
 * ObjectMapper} for JSON serialization and deserialization.
 *
 * <p>This converter is tailored for the authentication service to handle message conversion between
 * Java objects and AMQP messages. It currently supports only the {@link #toMessage(Object,
 * MessageProperties)} direction (object → JSON bytes). The reverse operation {@link
 * #fromMessage(Message)} is intentionally not implemented (returns {@code null}) as of now, but is
 * kept for future expansion.
 *
 * <p>The converter sets the content type of the message to {@link
 * MessageProperties#CONTENT_TYPE_JSON} to inform consumers about the payload format.
 *
 * <p>It wraps any serialization exception in a {@link MessageConversionException} to adhere to the
 * Spring AMQP contract.
 *
 * @author Your Team
 * @see MessageConverter
 * @see ObjectMapper
 * @since 1.0
 */
public class CustomMessageConverter implements MessageConverter {

  /** The Jackson {@link ObjectMapper} used for JSON processing. */
  private ObjectMapper objectMapper;

  /**
   * Constructs a new {@code CustomMessageConverter} with the given {@link ObjectMapper}.
   *
   * @param objectMapper the Jackson {@code ObjectMapper} to use for serialization/deserialization
   *     (must not be {@code null})
   */
  public CustomMessageConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Converts a Java object into an AMQP {@link Message} with JSON payload.
   *
   * <p>This method serializes the given object to a JSON byte array using the configured {@link
   * ObjectMapper}. It then sets the content type of the provided {@link MessageProperties} to
   * {@code application/json} and creates a new {@link Message} combining the bytes and the
   * properties.
   *
   * <p>If serialization fails (e.g., due to unsupported types or cyclic references), a {@link
   * MessageConversionException} is thrown with the original exception as its cause.
   *
   * @param object the object to convert (may be {@code null})
   * @param messageProperties the message properties to apply (must not be {@code null}; its content
   *     type will be set)
   * @return a new {@link Message} containing the JSON bytes and the updated properties
   * @throws MessageConversionException if the object cannot be serialized to JSON
   */
  @Override
  public Message toMessage(Object object, MessageProperties messageProperties)
      throws MessageConversionException {

    try {
      byte[] convertedMessage = this.objectMapper.writeValueAsBytes(object);

      messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);

      return new Message(convertedMessage, messageProperties);
    } catch (Exception exception) {
      throw new MessageConversionException("Failed to convert object to JSON", exception);
    }
  }

  /**
   * Converts an AMQP {@link Message} back into a Java object.
   *
   * <p><strong>Note:</strong> This method is currently not implemented and always returns {@code
   * null}. The commented-out code indicates a previous intention to deserialize into a {@code
   * ResponseDto} type, but it has been left disabled. This may be completed in a future iteration
   * when the service requires inbound message consumption.
   *
   * <p>If this method were implemented, it would parse the message body (JSON bytes) using the
   * {@link ObjectMapper} and transform it into the appropriate domain object. Any parsing error
   * would be wrapped in a {@link MessageConversionException}.
   *
   * @param message the AMQP message to convert (may be {@code null}, but the contract typically
   *     expects a non-null message)
   * @return always {@code null} in the current implementation
   */
  @Override
  public Object fromMessage(Message message) throws MessageConversionException {

    //        try {
    //            return this.objectMapper.readValue(message.getBody(), ResponseDto.class);
    //        } catch (Exception exception) {
    //            throw new MessageConversionException("Failed to convert to JSON to object",
    // exception);
    //        }

    return null;
  }
}
