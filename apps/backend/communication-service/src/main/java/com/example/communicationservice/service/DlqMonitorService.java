package com.example.communicationservice.service;

import com.example.commonlib.event.CommunicationRoutingKeys;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Continuously monitors Dead Letter Queues for stuck messages (permanently failed after exhausting
 * all in‑code retries and circuit‑breaker fallbacks).
 *
 * <p>Exposes {@code communication.dlq.depth} as a Prometheus gauge, with a tag for each monitored
 * DLQ. An alert rule (e.g., {@code > 0 for 5m}) can be configured based on this metric.
 *
 * <p>Also logs an {@code ERROR} level message whenever a DLQ is non‑empty, making it suitable for
 * paging/alerting integrations.
 *
 * @see CommunicationRoutingKeys
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DlqMonitorService {

  private final RabbitAdmin rabbitAdmin;
  private final MeterRegistry meterRegistry;

  private static final String[] MONITORED_DLQS = {
    CommunicationRoutingKeys.DLQ_OTP_EMAIL, CommunicationRoutingKeys.DLQ_OTP_SMS
  };

  /** Backing map for Micrometer gauges — gauges read live values from here on each scrape. */
  private final Map<String, Long> depthByQueue = new ConcurrentHashMap<>();

  /**
   * Registers a gauge for each monitored DLQ after the bean is constructed. The gauge reads the
   * current depth from {@link #depthByQueue}.
   */
  @PostConstruct
  public void registerGauges() {
    for (String queue : MONITORED_DLQS) {
      depthByQueue.put(queue, 0L);
      Gauge.builder("communication.dlq.depth", depthByQueue, m -> m.getOrDefault(queue, 0L))
          .tag("queue", queue)
          .description("Number of messages currently stuck in this dead-letter queue")
          .register(meterRegistry);
    }
  }

  /**
   * Polls each DLQ's depth every 30 seconds.
   *
   * <p>Logs an {@code ERROR} (suitable for alerting) whenever a DLQ is non‑empty. The current depth
   * is also stored in the backing map for Prometheus scraping.
   */
  @Scheduled(fixedDelay = 30_000)
  public void checkDlqDepths() {
    for (String queue : MONITORED_DLQS) {
      QueueInformation info = rabbitAdmin.getQueueInfo(queue);
      long depth = info != null ? info.getMessageCount() : -1;

      if (depth > 0) {
        log.error("event=dlq_not_empty queue={} depth={}", queue, depth);
      }

      depthByQueue.put(queue, depth);

      if (depth > 0) {
        log.error("event=dlq_not_empty queue={} depth={} action_required=true", queue, depth);
        // TODO: wire to PagerDuty/Slack webhook — see alert() below
      } else {
        log.debug("event=dlq_check_ok queue={} depth=0", queue);
      }
    }
  }
}
