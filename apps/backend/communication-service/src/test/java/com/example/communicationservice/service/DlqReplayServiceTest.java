package com.example.communicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class DlqReplayServiceTest {

  @Test
  void replay_movesEachMessageFromDlqToTargetQueue_untilDlqIsEmpty() {
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    Message m1 = new Message("body1".getBytes(), new MessageProperties());
    Message m2 = new Message("body2".getBytes(), new MessageProperties());
    when(rabbitTemplate.receive(eq("dlq.email"), eq(1000L))).thenReturn(m1, m2, null);
    DlqReplayService service = new DlqReplayService(rabbitTemplate);

    int replayed = service.replay("dlq.email", "queue.email");

    assertThat(replayed).isEqualTo(2);
    verify(rabbitTemplate).send("queue.email", m1);
    verify(rabbitTemplate).send("queue.email", m2);
  }

  @Test
  void replay_emptyDlq_returnsZeroAndSendsNothing() {
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    when(rabbitTemplate.receive(anyString(), eq(1000L))).thenReturn(null);
    DlqReplayService service = new DlqReplayService(rabbitTemplate);

    int replayed = service.replay("dlq.sms", "queue.sms");

    assertThat(replayed).isZero();
    verify(rabbitTemplate, times(0)).send(anyString(), org.mockito.ArgumentMatchers.any(Message.class));
  }

  @Test
  void replay_stopsAtMaxBatchSizeEvenIfMoreMessagesAreAvailable() {
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    Message message = new Message("body".getBytes(), new MessageProperties());
    when(rabbitTemplate.receive(anyString(), eq(1000L))).thenReturn(message);
    DlqReplayService service = new DlqReplayService(rabbitTemplate);

    int replayed = service.replay("dlq.email", "queue.email");

    assertThat(replayed).isEqualTo(100);
    verify(rabbitTemplate, times(100)).send(eq("queue.email"), eq(message));
  }
}
