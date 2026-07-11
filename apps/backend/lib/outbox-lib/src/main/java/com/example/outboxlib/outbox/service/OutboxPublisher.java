package com.example.outboxlib.outbox.service;

import com.example.outboxlib.outbox.entity.OutboxEvent;
import com.example.outboxlib.outbox.entity.OutboxEvent.EventStatus;
import com.example.outboxlib.outbox.repository.OutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox publisher that guarantees at-least-once delivery without coupling the RabbitMQ send to the
 * database transaction.
 *
 * <p>Design invariant: the database is the source of truth. RabbitMQ is a secondary, best-effort
 * sink. We never let a RabbitMQ failure roll back a DB commit, and we never let a DB failure leave
 * a message "invisibly" published.
 *
 * <p>The publisher runs three scheduled tasks:
 *
 * <ul>
 *   <li><b>publishPendingEvents</b> – polls every 5 seconds for pending events and attempts to
 *       publish them.
 *   <li><b>retryFailedEvents</b> – every 60 seconds, resets failed events (with retries left) back
 *       to PENDING.
 *   <li><b>recoverStuckProcessingEvents</b> – every 120 seconds, resets events stuck in PROCESSING
 *       for too long.
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

  private final OutboxRepository outboxRepository;
  private final RabbitTemplate rabbitTemplate;
  private final TransactionTemplate transactionTemplate;

  private static final int MAX_RETRIES = 5;
  private static final long STUCK_PROCESSING_MINUTES = 2;

  // ------------------------------------------------------------------
  // 1. POLL & PUBLISH PENDING EVENTS  (every 5 seconds)
  // ------------------------------------------------------------------

  /**
   * Polls the outbox table for pending events with retry count less than maximum, and attempts to
   * publish them one by one.
   *
   * <p>Each event goes through a two‑phase DB update (PROCESSING → PUBLISHED) with the actual
   * RabbitMQ send in between, outside any transaction.
   */
  @Scheduled(fixedDelay = 5000)
  public void publishPendingEvents() {
    List<OutboxEvent> pending =
        outboxRepository.findByStatusAndRetryCountLessThanOrderByUpdatedAtAsc(
            EventStatus.PENDING, MAX_RETRIES);

    if (pending.isEmpty()) {
      log.debug("event=outbox_publish_no_pending");
      return;
    }

    log.debug("event=outbox_publish_batch size={}", pending.size());
    pending.forEach(this::publishOne);
  }

  /**
   * Publishes a single event using a two-phase DB write:
   *
   * <ol>
   *   <li><b>Phase 1 (TX):</b> mark as PROCESSING and flush
   *   <li><b>Phase 2:</b> send to RabbitMQ (non‑transactional)
   *   <li><b>Phase 3 (TX):</b> mark as PUBLISHED and flush
   * </ol>
   *
   * <p>If Phase 2 or 3 throws, the event remains PROCESSING. The {@link
   * #recoverStuckProcessingEvents()} job will eventually revert it to PENDING for retry.
   *
   * @param event the outbox event to publish
   */
  private void publishOne(OutboxEvent event) {
    // -------- PHASE 1: claim the row in DB (inside TX) ----------
    boolean claimed =
        Boolean.TRUE.equals(
            transactionTemplate.execute(
                status -> {
                  try {
                    // Optimistic claim: only proceed if still PENDING
                    OutboxEvent fresh = outboxRepository.findById(event.getId()).orElse(null);

                    if (fresh == null || fresh.getStatus() != EventStatus.PENDING) {
                      log.warn("event=outbox_skip_claimed eventId={}", event.getEventId());
                      return false; // another thread got it
                    }

                    fresh.setStatus(EventStatus.PROCESSING);
                    fresh.setUpdatedAt(LocalDateTime.now());
                    outboxRepository.save(fresh);
                    return true;
                  } catch (Exception e) {
                    status.setRollbackOnly();
                    throw e;
                  }
                }));

    if (!claimed) {
      return; // raced against another pod / thread
    }

    // -------- PHASE 2: fire-and-forget to RabbitMQ (OUTSIDE TX) ---
    try {
      Message message =
          MessageBuilder.withBody(event.getPayload().getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setMessageId(event.getEventId())
              .setHeader("eventType", event.getEventType())
              .setHeader("aggregateType", event.getAggregateType())
              .build();

      rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), message);
      log.debug("event=outbox_rabbit_send_success eventId={}", event.getEventId());
    } catch (Exception e) {
      // Do NOT mark FAILED here. Let the stuck-recovery job handle it
      // so we don't accidentally save FAILED while another thread may
      // have already recovered it to PENDING.
      log.error(
          "event=outbox_rabbit_send_failed eventId={} retryCount={}",
          event.getEventId(),
          event.getRetryCount(),
          e);
      return;
    }

    // -------- PHASE 3: confirm publication in DB (inside TX) ------
    transactionTemplate.executeWithoutResult(
        status -> {
          try {
            event.setStatus(EventStatus.PUBLISHED);
            event.setProcessedAt(LocalDateTime.now());
            event.setUpdatedAt(LocalDateTime.now());
            outboxRepository.save(event);
            log.info(
                "event=outbox_published eventId={} type={} aggregateId={}",
                event.getEventId(),
                event.getEventType(),
                event.getAggregateId());
          } catch (Exception e) {
            status.setRollbackOnly();
            throw e;
          }
        });
  }

  // ------------------------------------------------------------------
  // 2. RETRY FAILED EVENTS  (every 60 seconds)
  // ------------------------------------------------------------------

  /**
   * Resets failed events (with retry count less than maximum) back to PENDING so they are picked up
   * again by {@link #publishPendingEvents()}.
   *
   * <p>This runs every 60 seconds and updates the status atomically in a transaction.
   */
  @Scheduled(fixedDelay = 60000)
  public void retryFailedEvents() {
    // Executed inside a proxy-created TX because the scheduler
    // calls the *proxy*, not a self-reference.
    List<OutboxEvent> failed =
        outboxRepository.findByStatusAndRetryCountLessThanOrderByUpdatedAtAsc(
            EventStatus.FAILED, MAX_RETRIES);

    if (failed.isEmpty()) {
      log.debug("event=outbox_retry_no_failed");
      return;
    }

    log.info("event=outbox_retry_batch size={}", failed.size());

    // Batch update atomically
    transactionTemplate.executeWithoutResult(
        status -> {
          LocalDateTime now = LocalDateTime.now();
          failed.forEach(
              e -> {
                e.setStatus(EventStatus.PENDING);
                e.setUpdatedAt(now);
              });
          outboxRepository.saveAll(failed);
        });

    log.info("event=outbox_retry_batch_completed size={}", failed.size());
  }

  // ------------------------------------------------------------------
  // 3. RECOVER STUCK PROCESSING EVENTS  (every 120 seconds)
  // ------------------------------------------------------------------

  /**
   * Recovers events that have been stuck in the PROCESSING state for longer than {@value
   * #STUCK_PROCESSING_MINUTES} minutes.
   *
   * <p>Such events are reset to PENDING to allow re‑processing. This handles cases where a
   * publisher crashed or the RabbitMQ send hung indefinitely.
   */
  @Scheduled(fixedDelay = 120000)
  public void recoverStuckProcessingEvents() {
    LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_PROCESSING_MINUTES);

    List<OutboxEvent> stuck =
        outboxRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            EventStatus.PROCESSING, threshold);

    if (stuck.isEmpty()) {
      log.debug("event=outbox_recover_no_stuck");
      return;
    }

    log.warn("event=outbox_recover_stuck size={}", stuck.size());

    transactionTemplate.executeWithoutResult(
        status -> {
          LocalDateTime now = LocalDateTime.now();
          stuck.forEach(
              e -> {
                e.setStatus(EventStatus.PENDING);
                e.setUpdatedAt(now);
              });
          outboxRepository.saveAll(stuck);
        });

    log.warn("event=outbox_recover_stuck_completed size={}", stuck.size());
  }
}
