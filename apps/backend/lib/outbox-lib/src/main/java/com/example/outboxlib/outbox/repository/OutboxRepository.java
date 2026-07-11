package com.example.outboxlib.outbox.repository;

import com.example.outboxlib.outbox.entity.OutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OutboxEvent} entities.
 *
 * <p>Provides custom query methods used by the outbox publisher to fetch events in various states
 * for processing, retrying, and recovery.
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

  /**
   * Finds all pending events, ordered by creation time ascending.
   *
   * @param status the status to filter by (expected {@code PENDING})
   * @return list of events
   */
  List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.EventStatus status);

  /**
   * Finds events with a given status and a retry count less than the specified maximum, ordered by
   * creation time ascending.
   *
   * @param status the status (e.g., {@code PENDING}, {@code FAILED})
   * @param maxRetries maximum retry count (exclusive)
   * @return list of events
   */
  List<OutboxEvent> findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
      OutboxEvent.EventStatus status, Integer maxRetries);

  /**
   * Finds events with a given status and a retry count less than the specified maximum, ordered by
   * update time ascending (used for pending events to pick oldest first).
   *
   * @param status the status (e.g., {@code PENDING})
   * @param maxRetries maximum retry count (exclusive)
   * @return list of events
   */
  List<OutboxEvent> findByStatusAndRetryCountLessThanOrderByUpdatedAtAsc(
      OutboxEvent.EventStatus status, int maxRetries);

  /**
   * Finds events with a given status whose {@code updatedAt} is before the threshold, ordered by
   * update time ascending (used for recovery of stuck processing events).
   *
   * @param status the status (e.g., {@code PROCESSING})
   * @param threshold the cutoff timestamp
   * @return list of events that have been processing for too long
   */
  List<OutboxEvent> findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
      OutboxEvent.EventStatus status, LocalDateTime threshold);
}
