package com.example.outboxlib.inbox.entity;

import com.example.outboxlib.inbox.converter.EpochMillisConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Entity representing an inbox event for idempotent processing of incoming messages.
 *
 * <p>The inbox pattern ensures that each incoming event is processed exactly once, even in the
 * presence of broker redeliveries. Each event is identified by a unique {@code eventId} and tracks
 * its processing status (PENDING, PROCESSING, PROCESSED, FAILED).
 *
 * <p>This entity is used by the {@link com.example.outboxlib.inbox.repository.InboxRepository} and
 * is managed by the {@link com.example.communicationservice.listener.CommunicationConsumer}.
 */
@Entity
@Table(
    name = "inbox_events",
    indexes = {
      @Index(name = "idx_inbox_event_id", columnList = "event_id", unique = true),
      @Index(name = "idx_inbox_status", columnList = "status")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboxEvent {

  /** Primary key. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Unique identifier of the original event (e.g., from the message ID). */
  @Column(nullable = false, unique = true)
  private String eventId;

  /** Logical type of the event (e.g., "OTP_EMAIL", "OTP_SMS"). */
  @Column(nullable = false)
  private String eventType;

  /** JSON payload of the event. */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  /** Current processing status of this inbox event. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ProcessStatus status = ProcessStatus.PENDING;

  /** Number of retry attempts made so far. */
  private Integer retryCount;

  /** Error message if the event failed (set when status is FAILED). */
  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  /** Timestamp when the event was successfully processed (set when status becomes PROCESSED). */
  @Convert(converter = EpochMillisConverter.class)
  private LocalDateTime processedAt;

  /** Timestamp when the event was first received (set automatically on persist). */
  @Convert(converter = EpochMillisConverter.class)
  @Column(nullable = false, updatable = false)
  private LocalDateTime receivedAt;

  /** Enumeration of possible processing states. */
  public enum ProcessStatus {
    /** Event has been received but not yet claimed. */
    PENDING,
    /** Event is currently being processed (claimed). */
    PROCESSING,
    /** Event has been successfully processed. */
    PROCESSED,
    /** Processing failed after all retries. */
    FAILED
  }

  /**
   * Callback method invoked before the entity is persisted.
   *
   * <p>Sets {@code receivedAt} to the current time if not already set, and initialises {@code
   * retryCount} to 0 if {@code null}.
   */
  @PrePersist
  protected void onCreate() {
    if (this.receivedAt == null) {
      this.receivedAt = LocalDateTime.now();
    }
    if (this.retryCount == null) {
      this.retryCount = 0;
    }
  }
}
