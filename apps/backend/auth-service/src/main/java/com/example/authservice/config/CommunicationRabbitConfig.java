package com.example.authservice.config;

import com.example.commonlib.event.CommunicationRoutingKeys;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * RabbitMQ configuration for the authentication service's communication layer.
 *
 * <p>This class sets up the messaging infrastructure required to publish and consume events via
 * RabbitMQ. It defines the following key beans:
 *
 * <ul>
 *   <li>A {@link TopicExchange} for routing messages based on routing keys.
 *   <li>A Jackson {@link ObjectMapper} for JSON serialization/deserialization.
 *   <li>A custom {@link MessageConverter} ({@link CustomMessageConverter}) that uses the configured
 *       {@code ObjectMapper}.
 *   <li>A {@link RabbitTemplate} pre-configured with the custom message converter and a reply
 *       timeout.
 * </ul>
 *
 * <p>The exchange name is obtained from {@link CommunicationRoutingKeys#EXCHANGE}, ensuring
 * consistency across services. All beans are scoped as singletons by default, suitable for
 * thread-safe concurrent message processing.
 *
 * @author Your Team
 * @see CommunicationRoutingKeys
 * @see CustomMessageConverter
 * @since 1.0
 */
@Configuration
public class CommunicationRabbitConfig {

  /**
   * Creates a durable, non-auto-delete topic exchange for routing authentication-related events.
   *
   * <p>The exchange is declared with:
   *
   * <ul>
   *   <li><strong>name</strong>: the value from {@code CommunicationRoutingKeys.EXCHANGE}
   *   <li><strong>durable</strong>: {@code true} – survives broker restarts
   *   <li><strong>auto-delete</strong>: {@code false} – not deleted when the last queue is unbound
   * </ul>
   *
   * <p>This exchange is used by all publishers and consumers within the authentication service to
   * send and receive messages related to user authentication, authorization, and account lifecycle
   * events.
   *
   * @return a {@link TopicExchange} instance configured as described
   */
  @Bean
  public TopicExchange notificationExchange() {
    return new TopicExchange(CommunicationRoutingKeys.EXCHANGE, true, false);
  }

  /**
   * Provides a default Jackson {@link ObjectMapper} for JSON processing.
   *
   * <p>The returned instance uses standard configuration and is injected into the {@link
   * CustomMessageConverter} to handle serialization and deserialization of message payloads. It can
   * be overridden or customized in other configuration classes if needed.
   *
   * @return a new {@link ObjectMapper} instance
   */
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  /**
   * Creates a custom message converter that delegates JSON conversion to the provided {@link
   * ObjectMapper}.
   *
   * <p>This converter is used by the {@link RabbitTemplate} to transform Java objects to and from
   * AMQP message bodies. The custom implementation allows fine‑grained control over serialization,
   * such as handling type information or custom date formats.
   *
   * @param objectMapper the Jackson {@code ObjectMapper} to use for actual JSON processing
   *     (injected from the {@link #objectMapper()} bean)
   * @return a {@link MessageConverter} instance, specifically a {@code CustomMessageConverter}
   */
  @Bean
  public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
    return new CustomMessageConverter(objectMapper);
  }

  /**
   * Configures the primary {@link RabbitTemplate} for sending and receiving messages via RabbitMQ.
   *
   * <p>The template is set up with:
   *
   * <ul>
   *   <li>The provided {@link ConnectionFactory} for managing connections and channels.
   *   <li>The custom {@link MessageConverter} to handle serialization.
   *   <li>A reply timeout of 5000 milliseconds for RPC-style operations.
   * </ul>
   *
   * <p>This bean is intended for use by service classes that need to publish events or perform
   * synchronous request-response messaging.
   *
   * @param connectionFactory the RabbitMQ connection factory (provided by Spring Boot
   *     auto-configuration)
   * @param jsonMessageConverter the message converter to be used by the template (injected from
   *     {@link #jsonMessageConverter(ObjectMapper)})
   * @return a fully configured {@link RabbitTemplate} instance
   */
  @Bean
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);

    rabbitTemplate.setMessageConverter(jsonMessageConverter);

    rabbitTemplate.setReplyTimeout(5000);
    return rabbitTemplate;
  }
}
