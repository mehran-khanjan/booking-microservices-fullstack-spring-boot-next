package com.example.communicationservice.monitoring;

import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

/**
 * Reads RabbitMQ's {@code x-death} header to determine how many times a message has been
 * dead‑lettered from a specific queue, cumulatively.
 *
 * <p>This count survives manual DLQ replays because the broker keys entries by (queue, reason) and
 * increments the count in place rather than resetting.
 *
 * @see <a href="https://www.rabbitmq.com/docs/dlx">RabbitMQ DLX documentation</a>
 */
@Component
public class DeathCountExtractor {

  private static final String X_DEATH_HEADER = "x-death";

  /**
   * Extracts the cumulative death count for a message from the given origin queue.
   *
   * @param props the {@link MessageProperties} of the RabbitMQ message
   * @param originQueue the name of the queue from which the message was originally consumed
   * @return the total recorded deaths for that queue, or {@code 0} if the message was never
   *     dead‑lettered from it
   */
  @SuppressWarnings("unchecked")
  public long extractDeathCount(MessageProperties props, String originQueue) {
    List<Map<String, Object>> xDeath =
        (List<Map<String, Object>>) props.getHeaders().get(X_DEATH_HEADER);
    if (xDeath == null || xDeath.isEmpty()) return 0L;

    return xDeath.stream()
        .filter(entry -> originQueue.equals(entry.get("queue")))
        .map(entry -> (Long) entry.get("count"))
        .findFirst()
        .orElse(0L);
  }
}
