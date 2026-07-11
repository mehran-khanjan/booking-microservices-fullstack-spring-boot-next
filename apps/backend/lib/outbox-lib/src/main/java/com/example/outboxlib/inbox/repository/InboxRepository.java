package com.example.outboxlib.inbox.repository;

import com.example.outboxlib.inbox.entity.InboxEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link InboxEvent} entities.
 *
 * <p>Provides standard CRUD operations as well as custom query methods for finding events by their
 * unique event ID and checking existence.
 */
@Repository
public interface InboxRepository extends JpaRepository<InboxEvent, Long> {

  /**
   * Retrieves an inbox event by its unique {@code eventId}.
   *
   * @param eventId the unique identifier of the event
   * @return an {@link Optional} containing the event if found, otherwise empty
   */
  Optional<InboxEvent> findByEventId(String eventId);

  /**
   * Checks whether an inbox event with the given {@code eventId} exists.
   *
   * @param eventId the unique identifier of the event
   * @return {@code true} if an event exists, {@code false} otherwise
   */
  boolean existsByEventId(String eventId);
}
