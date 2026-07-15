package com.example.communicationservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.commonlib.event.CommunicationRoutingKeys;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

class CommunicationRabbitConfigTest {

  private final CommunicationRabbitConfig config = new CommunicationRabbitConfig();

  @Test
  void communicationExchange_isDurableNonAutoDeleteTopicExchange() {
    TopicExchange exchange = config.communicationExchange();

    assertThat(exchange.getName()).isEqualTo(CommunicationRoutingKeys.EXCHANGE);
    assertThat(exchange.isDurable()).isTrue();
    assertThat(exchange.isAutoDelete()).isFalse();
  }

  @Test
  void communicationDlx_isDurableNonAutoDeleteTopicExchange() {
    TopicExchange dlx = config.communicationDlx();

    assertThat(dlx.getName()).isEqualTo(CommunicationRoutingKeys.DLX_EXCHANGE);
    assertThat(dlx.isDurable()).isTrue();
    assertThat(dlx.isAutoDelete()).isFalse();
  }

  @Test
  void otpEmailQueue_isDurableAndRoutesDeadLettersToDlx() {
    Queue queue = config.otpEmailQueue();

    assertThat(queue.getName()).isEqualTo(CommunicationRoutingKeys.QUEUE_OTP_EMAIL);
    assertThat(queue.isDurable()).isTrue();
    assertThat(queue.getArguments())
        .containsEntry("x-dead-letter-exchange", CommunicationRoutingKeys.DLX_EXCHANGE)
        .containsEntry("x-dead-letter-routing-key", CommunicationRoutingKeys.DLQ_OTP_EMAIL);
  }

  @Test
  void otpSmsQueue_isDurableAndRoutesDeadLettersToDlx() {
    Queue queue = config.otpSmsQueue();

    assertThat(queue.getName()).isEqualTo(CommunicationRoutingKeys.QUEUE_OTP_SMS);
    assertThat(queue.getArguments())
        .containsEntry("x-dead-letter-exchange", CommunicationRoutingKeys.DLX_EXCHANGE)
        .containsEntry("x-dead-letter-routing-key", CommunicationRoutingKeys.DLQ_OTP_SMS);
  }

  @Test
  void otpEmailDlq_isDurableQueue() {
    Queue dlq = config.otpEmailDlq();

    assertThat(dlq.getName()).isEqualTo(CommunicationRoutingKeys.DLQ_OTP_EMAIL);
    assertThat(dlq.isDurable()).isTrue();
  }

  @Test
  void otpSmsDlq_isDurableQueue() {
    Queue dlq = config.otpSmsDlq();

    assertThat(dlq.getName()).isEqualTo(CommunicationRoutingKeys.DLQ_OTP_SMS);
    assertThat(dlq.isDurable()).isTrue();
  }

  @Test
  void otpEmailBinding_bindsQueueToExchangeWithOtpEmailRoutingKey() {
    Binding binding = config.otpEmailBinding();

    assertThat(binding.getDestination()).isEqualTo(CommunicationRoutingKeys.QUEUE_OTP_EMAIL);
    assertThat(binding.getExchange()).isEqualTo(CommunicationRoutingKeys.EXCHANGE);
    assertThat(binding.getRoutingKey()).isEqualTo(CommunicationRoutingKeys.OTP_EMAIL);
  }

  @Test
  void otpSmsBinding_bindsQueueToExchangeWithOtpSmsRoutingKey() {
    Binding binding = config.otpSmsBinding();

    assertThat(binding.getDestination()).isEqualTo(CommunicationRoutingKeys.QUEUE_OTP_SMS);
    assertThat(binding.getExchange()).isEqualTo(CommunicationRoutingKeys.EXCHANGE);
    assertThat(binding.getRoutingKey()).isEqualTo(CommunicationRoutingKeys.OTP_SMS);
  }

  @Test
  void otpEmailDlqBinding_bindsDlqToDlxWithDlqRoutingKey() {
    Binding binding = config.otpEmailDlqBinding();

    assertThat(binding.getDestination()).isEqualTo(CommunicationRoutingKeys.DLQ_OTP_EMAIL);
    assertThat(binding.getExchange()).isEqualTo(CommunicationRoutingKeys.DLX_EXCHANGE);
    assertThat(binding.getRoutingKey()).isEqualTo(CommunicationRoutingKeys.DLQ_OTP_EMAIL);
  }

  @Test
  void otpSmsDlqBinding_bindsDlqToDlxWithDlqRoutingKey() {
    Binding binding = config.otpSmsDlqBinding();

    assertThat(binding.getDestination()).isEqualTo(CommunicationRoutingKeys.DLQ_OTP_SMS);
    assertThat(binding.getExchange()).isEqualTo(CommunicationRoutingKeys.DLX_EXCHANGE);
    assertThat(binding.getRoutingKey()).isEqualTo(CommunicationRoutingKeys.DLQ_OTP_SMS);
  }

  @Test
  void objectMapper_returnsNonNullInstance() {
    assertThat(config.objectMapper()).isNotNull();
  }

  @Test
  void jsonMessageConverter_wrapsCustomMessageConverterAroundGivenObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();

    MessageConverter converter = config.jsonMessageConverter(objectMapper);

    assertThat(converter).isInstanceOf(CustomMessageConverter.class);
  }

  @Test
  void rabbitListenerContainerFactory_isConfiguredWithConverterAndConcurrencyLimits() {
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    MessageConverter messageConverter = mock(MessageConverter.class);

    SimpleRabbitListenerContainerFactory factory =
        config.rabbitListenerContainerFactory(connectionFactory, messageConverter);

    assertThat(factory).isNotNull();
    assertThat((Boolean) ReflectionTestUtils.getField(factory, "defaultRequeueRejected")).isFalse();
  }

  @Test
  void rabbitAdmin_isCreatedFromConnectionFactory() {
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

    RabbitAdmin admin = config.rabbitAdmin(connectionFactory);

    assertThat(admin).isNotNull();
  }
}
