package com.example.outboxlib.outbox.entity;

import com.example.outboxlib.inbox.converter.EpochMillisConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Entity representing an outbox event for reliable message delivery.
 *
 * <p>The outbox pattern ensures that messages are persisted in the same database transaction as the
 * business logic, and then asynchronously published to a message broker. This guarantees
 * at-least-once delivery even if the broker is temporarily unavailable.
 *
 * <p>Each outbox event stores the full payload, routing information, and a status that tracks
 * publication progress (PENDING, PROCESSING, PUBLISHED, FAILED).
 *
 * @see com.example.outboxlib.outbox.service.OutboxPublisher
 * @see com.example.outboxlib.outbox.service.OutboxService
 */
@Entity
@Table(
    name = "outbox_events",
    indexes = {
      @Index(name = "idx_outbox_status", columnList = "status"),
      @Index(name = "idx_outbox_created", columnList = "created_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

  /** Primary key. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Unique identifier for this outbox event (generated as a UUID). */
  @Column(nullable = false, unique = true)
  private String eventId;

  /** Logical event type (e.g., "OTP_EMAIL"). */
  @Column(nullable = false)
  private String eventType;

  /** Domain aggregate type (e.g., "User"). */
  @Column(nullable = false)
  private String aggregateType;

  /** Identifier of the aggregate (e.g., user email or ID). */
  @Column(nullable = false)
  private String aggregateId;

  /** JSON payload of the event. */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  /** Current publication status. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private EventStatus status = EventStatus.PENDING;

  /** RabbitMQ routing key. */
  @Column(nullable = false)
  private String routingKey;

  /** RabbitMQ exchange name. */
  @Column(nullable = false)
  private String exchange;

  /** Number of publication attempts made so far. */
  @Builder.Default private Integer retryCount = 0;

  /** Error message if publication failed (set when status is FAILED). */
  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  /** Timestamp when the event was created. */
  @Convert(converter = EpochMillisConverter.class)
  @Column(nullable = false)
  private LocalDateTime createdAt;

  /** Timestamp of the last update. */
  @Convert(converter = EpochMillisConverter.class)
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /** Timestamp when the event was successfully published (set when status becomes PUBLISHED). */
  @Convert(converter = EpochMillisConverter.class)
  private LocalDateTime processedAt;

  /** Enumeration of possible publication states. */
  public enum EventStatus {
    /** Event is waiting to be published. */
    PENDING,
    /** Event is currently being published (claimed by a publisher). */
    PROCESSING,
    /** Event has been successfully published to RabbitMQ. */
    PUBLISHED,
    /** Publication failed after exhausting retries. */
    FAILED
  }

  /**
   * Callback method invoked before the entity is updated.
   *
   * <p>Sets {@code updatedAt} to the current time.
   */
  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * Callback method invoked before the entity is persisted.
   *
   * <p>Sets {@code createdAt} and {@code updatedAt} to the current time if not already set.
   */
  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();

    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
  }
}
