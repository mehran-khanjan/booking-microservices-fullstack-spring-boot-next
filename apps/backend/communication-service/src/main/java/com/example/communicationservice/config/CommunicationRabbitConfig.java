package com.example.communicationservice.config;

import com.example.commonlib.event.CommunicationRoutingKeys;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * RabbitMQ configuration for the Communication Service.
 *
 * <p>Defines the main exchange, a dead‑letter exchange, queues for OTP email and SMS, their
 * respective dead‑letter queues, and all necessary bindings. Also configures a custom message
 * converter, a listener container factory with retry semantics, and a {@link RabbitAdmin} for
 * broker management.
 *
 * @see CommunicationRoutingKeys
 * @see CustomMessageConverter
 */
@Configuration
public class CommunicationRabbitConfig {

  /**
   * The main topic exchange for communication events.
   *
   * @return a durable, non‑auto‑delete {@link TopicExchange}
   */
  @Bean
  public TopicExchange communicationExchange() {
    return new TopicExchange(CommunicationRoutingKeys.EXCHANGE, true, false);
  }

  /**
   * The dead‑letter topic exchange. Messages that fail processing are routed here.
   *
   * @return a durable, non‑auto‑delete {@link TopicExchange}
   */
  @Bean
  public TopicExchange communicationDlx() {
    return new TopicExchange(CommunicationRoutingKeys.DLX_EXCHANGE, true, false);
  }

  /**
   * Queue for OTP email events. Messages are sent here from the main exchange.
   *
   * <p>The queue is configured with a dead‑letter exchange and routing key so that unprocessed
   * messages (after retries) land in the DLQ.
   *
   * @return a durable {@link Queue} with DLQ arguments
   */
  @Bean
  public Queue otpEmailQueue() {
    return QueueBuilder.durable(CommunicationRoutingKeys.QUEUE_OTP_EMAIL)
        .withArgument("x-dead-letter-exchange", CommunicationRoutingKeys.DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", CommunicationRoutingKeys.DLQ_OTP_EMAIL)
        .build();
  }

  /**
   * Dead‑letter queue for OTP email events.
   *
   * @return a durable {@link Queue}
   */
  @Bean
  public Queue otpEmailDlq() {
    return QueueBuilder.durable(CommunicationRoutingKeys.DLQ_OTP_EMAIL).build();
  }

  /**
   * Binds the OTP email queue to the main exchange using the routing key for OTP email.
   *
   * @return a {@link Binding}
   */
  @Bean
  public Binding otpEmailBinding() {
    return BindingBuilder.bind(otpEmailQueue())
        .to(communicationExchange())
        .with(CommunicationRoutingKeys.OTP_EMAIL);
  }

  /**
   * Binds the OTP email DLQ to the dead‑letter exchange.
   *
   * @return a {@link Binding}
   */
  @Bean
  public Binding otpEmailDlqBinding() {
    return BindingBuilder.bind(otpEmailDlq())
        .to(communicationDlx())
        .with(CommunicationRoutingKeys.DLQ_OTP_EMAIL);
  }

  /**
   * Queue for OTP SMS events, with DLQ configuration.
   *
   * @return a durable {@link Queue} with DLQ arguments
   */
  @Bean
  public Queue otpSmsQueue() {
    return QueueBuilder.durable(CommunicationRoutingKeys.QUEUE_OTP_SMS)
        .withArgument("x-dead-letter-exchange", CommunicationRoutingKeys.DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", CommunicationRoutingKeys.DLQ_OTP_SMS)
        .build();
  }

  /**
   * Dead‑letter queue for OTP SMS events.
   *
   * @return a durable {@link Queue}
   */
  @Bean
  public Queue otpSmsDlq() {
    return QueueBuilder.durable(CommunicationRoutingKeys.DLQ_OTP_SMS).build();
  }

  /**
   * Binds the OTP SMS queue to the main exchange.
   *
   * @return a {@link Binding}
   */
  @Bean
  public Binding otpSmsBinding() {
    return BindingBuilder.bind(otpSmsQueue())
        .to(communicationExchange())
        .with(CommunicationRoutingKeys.OTP_SMS);
  }

  /**
   * Binds the OTP SMS DLQ to the dead‑letter exchange.
   *
   * @return a {@link Binding}
   */
  @Bean
  public Binding otpSmsDlqBinding() {
    return BindingBuilder.bind(otpSmsDlq())
        .to(communicationDlx())
        .with(CommunicationRoutingKeys.DLQ_OTP_SMS);
  }

  /**
   * Jackson {@link ObjectMapper} for JSON serialisation/deserialisation.
   *
   * @return a new {@link ObjectMapper} instance
   */
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  /**
   * Custom message converter that uses the {@code eventType} header to determine the target class
   * for deserialisation.
   *
   * @param objectMapper the Jackson {@link ObjectMapper}
   * @return a {@link CustomMessageConverter}
   */
  @Bean
  public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
    return new CustomMessageConverter(objectMapper);
  }

  /**
   * Configures the {@link SimpleRabbitListenerContainerFactory} used by all {@code @RabbitListener}
   * endpoints.
   *
   * <p>Key settings:
   *
   * <ul>
   *   <li>Custom {@link MessageConverter}
   *   <li>Default requeue rejected = {@code false} → failed messages go to the DLQ
   *   <li>Concurrent consumers: 3 to 10
   * </ul>
   *
   * @param cf the RabbitMQ {@link ConnectionFactory}
   * @param messageConverter the configured {@link MessageConverter}
   * @return a configured {@link SimpleRabbitListenerContainerFactory}
   */
  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory cf, MessageConverter messageConverter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

    factory.setMessageConverter(messageConverter);
    factory.setConnectionFactory(cf);

    // Reject-without-requeue on unhandled exception -> message routed to DLQ instead of looping
    // forever.
    factory.setDefaultRequeueRejected(false);
    factory.setConcurrentConsumers(3);
    factory.setMaxConcurrentConsumers(10);

    return factory;
  }

  /**
   * {@link RabbitAdmin} bean for declaring exchanges, queues, and bindings at application startup
   * (auto‑declaration enabled by default).
   *
   * @param connectionFactory the RabbitMQ {@link ConnectionFactory}
   * @return a new {@link RabbitAdmin}
   */
  @Bean
  public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
    return new RabbitAdmin(connectionFactory);
  }
}
