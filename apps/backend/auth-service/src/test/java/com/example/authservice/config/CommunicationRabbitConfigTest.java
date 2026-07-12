package com.example.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.commonlib.event.CommunicationRoutingKeys;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.ObjectMapper;

class CommunicationRabbitConfigTest {

  private final CommunicationRabbitConfig config = new CommunicationRabbitConfig();

  @Test
  void notificationExchange_isDurable_andNotAutoDelete() {
    TopicExchange exchange = config.notificationExchange();

    assertThat(exchange.getName()).isEqualTo(CommunicationRoutingKeys.EXCHANGE);
    assertThat(exchange.isDurable()).isTrue();
    assertThat(exchange.isAutoDelete()).isFalse();
  }

  @Test
  void rabbitTemplate_usesGivenConnectionFactory_andJsonMessageConverter() {
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    MessageConverter converter = config.jsonMessageConverter(config.objectMapper());

    RabbitTemplate template = config.rabbitTemplate(connectionFactory, converter);

    assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
    assertThat(template.getMessageConverter()).isSameAs(converter);
  }

  @Test
  void customMessageConverter_toMessage_serializesObjectAsJson_withJsonContentType() {
    ObjectMapper objectMapper = config.objectMapper();
    CustomMessageConverter converter = new CustomMessageConverter(objectMapper);

    Message message =
        converter.toMessage(java.util.Map.of("key", "value"), new MessageProperties());

    assertThat(message.getMessageProperties().getContentType())
        .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    assertThat(new String(message.getBody())).contains("\"key\"").contains("\"value\"");
  }

  @Test
  void customMessageConverter_fromMessage_currentlyReturnsNull() {
    CustomMessageConverter converter = new CustomMessageConverter(config.objectMapper());

    Object result = converter.fromMessage(new Message("{}".getBytes(), new MessageProperties()));

    assertThat(result).isNull();
  }
}
