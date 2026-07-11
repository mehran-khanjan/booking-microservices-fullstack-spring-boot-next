package com.example.communicationservice.listener;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.commonlib.event.OtpEmailEvent;
import com.example.commonlib.event.OtpSmsEvent;
import com.example.communicationservice.monitoring.DeathCountExtractor;
import com.example.communicationservice.service.EmailService;
import com.example.communicationservice.service.SmsService;
import com.example.outboxlib.inbox.entity.InboxEvent;
import com.example.outboxlib.inbox.repository.InboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consumes events from the transactional outbox of producers.
 *
 * <p>Implements the <b>inbox pattern</b> for idempotent processing and failure tracking. Each
 * incoming event is recorded in the {@link InboxEvent} table before processing. This ensures that
 * duplicate messages (e.g., due to broker redeliveries) are ignored.
 *
 * <p>Processing flow:
 *
 * <ol>
 *   <li><b>Claim</b> – insert/update inbox record to {@code PROCESSING} state (DB transaction).
 *   <li><b>Execute</b> – call the appropriate service (email or SMS) outside the transaction.
 *   <li><b>Success</b> – update inbox status to {@code PROCESSED}.
 *   <li><b>Failure</b> – update inbox status to {@code FAILED} and rethrow so RabbitMQ sends the
 *       message to the DLQ.
 * </ol>
 *
 * @see InboxEvent
 * @see InboxRepository
 * @see DeathCountExtractor
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommunicationConsumer {

  private final EmailService emailService;
  private final SmsService smsService;
  private final ObjectMapper objectMapper;
  private final InboxRepository inboxRepository;
  private final TransactionTemplate transactionTemplate;
  private final DeathCountExtractor deathCountExtractor;

  @Value("${communication.max-redeliveries:3}")
  private int maxRedeliveries;

  /**
   * Listens to the OTP email queue and processes incoming email events.
   *
   * @param message the RabbitMQ message containing the event
   */
  @RabbitListener(
      queues = CommunicationRoutingKeys.QUEUE_OTP_EMAIL,
      containerFactory = "rabbitListenerContainerFactory")
  public void handleOtpEmail(Message message) {
    process(
        message,
        CommunicationRoutingKeys.QUEUE_OTP_EMAIL,
        "OTP_EMAIL",
        (eventId) -> {
          OtpEmailEvent event = objectMapper.readValue(message.getBody(), OtpEmailEvent.class);
          emailService.sendOtpEmail(event.toEmail(), event.otp(), event.expiryMinutes());
        });
  }

  /**
   * Listens to the OTP SMS queue and processes incoming SMS events.
   *
   * @param message the RabbitMQ message containing the event
   */
  @RabbitListener(
      queues = CommunicationRoutingKeys.QUEUE_OTP_SMS,
      containerFactory = "rabbitListenerContainerFactory")
  public void handleOtpSms(Message message) {
    process(
        message,
        CommunicationRoutingKeys.QUEUE_OTP_SMS,
        "OTP_SMS",
        (eventId) -> {
          OtpSmsEvent event = objectMapper.readValue(message.getBody(), OtpSmsEvent.class);
          smsService.sendOtp(event.toPhoneNumber(), event.otp(), event.expiryMinutes());
        });
  }

  /**
   * Shared processing pipeline for all event types.
   *
   * @param message the incoming RabbitMQ message
   * @param originQueue the queue from which the message was consumed (for logging/death‑count)
   * @param eventType the type of event (e.g., "OTP_EMAIL")
   * @param handler the business logic to execute (may throw exception)
   */
  private void process(
      Message message, String originQueue, String eventType, ThrowingConsumer<String> handler) {
    String eventId = message.getMessageProperties().getMessageId();
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
    long deathCount =
        deathCountExtractor.extractDeathCount(message.getMessageProperties(), originQueue);

    // ---------- Phase 1: Claim the inbox record (inside transaction) ----------
    boolean claimed =
        Boolean.TRUE.equals(
            transactionTemplate.execute(
                status -> {
                  try {
                    Optional<InboxEvent> existing = inboxRepository.findByEventId(eventId);

                    if (existing.isPresent()) {
                      InboxEvent inbox = existing.get();
                      // If already processed, we're done
                      if (inbox.getStatus() == InboxEvent.ProcessStatus.PROCESSED) {
                        log.info(
                            "event={}_already_processed eventId={}",
                            eventType.toLowerCase(),
                            eventId);
                        return false; // skip
                      }
                      // For any other state (PENDING, PROCESSING, FAILED), we allow reprocessing
                      // (we are on a retry). Increment retry count and set to PROCESSING.
                      inbox.setStatus(InboxEvent.ProcessStatus.PROCESSING);
                      inbox.setRetryCount(
                          inbox.getRetryCount() == null ? 1 : inbox.getRetryCount() + 1);
                      inbox.setErrorMessage(null); // clear previous error
                      inboxRepository.save(inbox);
                    } else {
                      // First time – create new inbox record with PENDING, then set to PROCESSING
                      InboxEvent newInbox =
                          InboxEvent.builder()
                              .eventId(eventId)
                              .eventType(eventType)
                              .payload(payload)
                              .status(InboxEvent.ProcessStatus.PENDING)
                              .retryCount(0)
                              .build();
                      // Save as PENDING first; then update to PROCESSING in same transaction
                      inboxRepository.save(newInbox);
                      // Now set to PROCESSING (we can just update the saved entity)
                      newInbox.setStatus(InboxEvent.ProcessStatus.PROCESSING);
                      newInbox.setRetryCount(1); // first attempt
                      inboxRepository.save(newInbox);
                    }
                    return true;
                  } catch (DataIntegrityViolationException e) {
                    // Duplicate eventId from concurrent insert – treat as already processed
                    log.warn(
                        "event={}_concurrent_duplicate eventId={}",
                        eventType.toLowerCase(),
                        eventId);
                    status.setRollbackOnly();
                    return false;
                  } catch (Exception e) {
                    status.setRollbackOnly();
                    log.error(
                        "event={}_inbox_claim_failed eventId={}",
                        eventType.toLowerCase(),
                        eventId,
                        e);
                    throw e;
                  }
                }));

    if (!claimed) {
      // Either already processed or duplicate – no further action
      return;
    }

    // ---------- Phase 2: Process the actual business logic (outside transaction) ----------
    try {
      log.info(
          "event={}_processing eventId={} deathCount={}",
          eventType.toLowerCase(),
          eventId,
          deathCount);
      handler.accept(eventId);
    } catch (Exception e) {
      // ---------- Phase 3: Update inbox to FAILED (inside transaction) ----------
      transactionTemplate.executeWithoutResult(
          txStatus -> {
            try {
              inboxRepository
                  .findByEventId(eventId)
                  .ifPresent(
                      inbox -> {
                        inbox.setStatus(InboxEvent.ProcessStatus.FAILED);
                        inbox.setErrorMessage(
                            e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                        // retryCount already incremented during claim
                        inboxRepository.save(inbox);
                      });
            } catch (Exception ex) {
              txStatus.setRollbackOnly();
              log.error(
                  "event={}_inbox_failed_update_error eventId={}",
                  eventType.toLowerCase(),
                  eventId,
                  ex);
            }
          });
      log.error(
          "event={}_processing_failed eventId={} deathCount={}",
          eventType.toLowerCase(),
          eventId,
          deathCount,
          e);
      throw new RuntimeException(e); // rethrow so RabbitMQ NACKs and requeues
    }

    // ---------- Phase 4: Update inbox to PROCESSED (inside transaction) ----------
    transactionTemplate.executeWithoutResult(
        txStatus -> {
          try {
            inboxRepository
                .findByEventId(eventId)
                .ifPresent(
                    inbox -> {
                      inbox.setStatus(InboxEvent.ProcessStatus.PROCESSED);
                      inbox.setProcessedAt(LocalDateTime.now());
                      inboxRepository.save(inbox);
                    });
          } catch (Exception e) {
            txStatus.setRollbackOnly();
            log.error(
                "event={}_inbox_processed_update_failed eventId={}",
                eventType.toLowerCase(),
                eventId,
                e);
            throw e;
          }
        });

    log.info("event={}_processed_success eventId={}", eventType.toLowerCase(), eventId);
  }

  /**
   * Functional interface for a consumer that may throw a checked exception.
   *
   * @param <T> the type of the input
   */
  @FunctionalInterface
  private interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;
  }
}
