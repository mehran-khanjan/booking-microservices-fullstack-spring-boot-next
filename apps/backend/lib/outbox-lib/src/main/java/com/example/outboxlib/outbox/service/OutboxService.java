package com.example.outboxlib.outbox.service;

import com.example.outboxlib.outbox.entity.OutboxEvent;
import com.example.outboxlib.outbox.repository.OutboxRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Generic transactional‑outbox writer.
 *
 * <p>Persists an event to the {@code outbox_events} table so it can be reliably relayed to RabbitMQ
 * by {@link OutboxPublisher}, decoupling the caller's business transaction from broker
 * availability.
 *
 * <p><b>Important:</b> The caller MUST already be inside an active transaction (see {@link
 * Propagation#MANDATORY}). This guarantees that the outbox row commits atomically with any other
 * local DB writes in the same use case.
 *
 * @see OutboxPublisher
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxService {

  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  /**
   * Serialises the given payload object to JSON and persists it as a PENDING outbox event.
   *
   * <p>The event is assigned a unique {@code eventId} (UUID) and the current status is set to
   * PENDING.
   *
   * @param payload the event body (any serialisable record/POJO)
   * @param eventType logical event name used by consumers to pick a deserialiser (e.g.,
   *     "OTP_EMAIL")
   * @param exchange target RabbitMQ exchange
   * @param routingKey target routing key
   * @param aggregateType domain aggregate this event relates to (e.g., "User")
   * @param aggregateId identifier of that aggregate (e.g., email/phone/userId)
   * @throws IllegalStateException if serialisation or persistence fails
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void saveEvent(
      Object payload,
      String eventType,
      String exchange,
      String routingKey,
      String aggregateType,
      String aggregateId) {
    try {
      String json = objectMapper.writeValueAsString(payload);

      OutboxEvent event =
          OutboxEvent.builder()
              .eventId(UUID.randomUUID().toString())
              .eventType(eventType)
              .aggregateType(aggregateType)
              .aggregateId(aggregateId)
              .payload(json)
              .status(OutboxEvent.EventStatus.PENDING)
              .routingKey(routingKey)
              .exchange(exchange)
              .retryCount(0)
              .build();

      outboxRepository.save(event);
      log.debug(
          "event=outbox_saved eventId={} type={} aggregateId={}",
          event.getEventId(),
          eventType,
          aggregateId);
    } catch (Exception e) {
      log.error("event=outbox_save_failed type={} aggregateId={}", eventType, aggregateId, e);
      throw new IllegalStateException("Failed to save event to outbox", e);
    }
  }
}
