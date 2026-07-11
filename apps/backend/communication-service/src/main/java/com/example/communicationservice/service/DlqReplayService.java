package com.example.communicationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Moves messages from a Dead‑Letter Queue (DLQ) back onto its original live queue for reprocessing.
 *
 * <p>Used by the admin controller to manually trigger replays. A safety cap of {@value #MAX_BATCH}
 * messages per invocation prevents accidental overload.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DlqReplayService {

  private final RabbitTemplate rabbitTemplate;
  private static final int MAX_BATCH = 100; // safety cap per invocation

  /**
   * Replays up to {@value #MAX_BATCH} messages from the specified DLQ to the target queue.
   *
   * <p>The method receives messages with a timeout of 1 second per message, then resends them to
   * the target queue.
   *
   * @param dlqName the name of the dead‑letter queue to consume from
   * @param targetQueueName the name of the queue to republish messages to
   * @return the number of messages actually replayed (between 0 and {@value #MAX_BATCH})
   */
  public int replay(String dlqName, String targetQueueName) {
    int count = 0;
    Message message;
    while (count < MAX_BATCH && (message = rabbitTemplate.receive(dlqName, 1000)) != null) {
      rabbitTemplate.send(targetQueueName, message);
      count++;
    }

    log.info("event=dlq_replayed dlq={} targetQueue={} count={}", dlqName, targetQueueName, count);

    return count;
  }
}
