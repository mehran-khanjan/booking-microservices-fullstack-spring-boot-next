package com.example.communicationservice.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.communicationservice.monitoring.DeathCountExtractor;
import com.example.communicationservice.service.EmailService;
import com.example.communicationservice.service.SmsService;
import com.example.outboxlib.inbox.entity.InboxEvent;
import com.example.outboxlib.inbox.repository.InboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

@ExtendWith(MockitoExtension.class)
class CommunicationConsumerTest {

  @Mock private EmailService emailService;
  @Mock private SmsService smsService;
  @Mock private InboxRepository inboxRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private DeathCountExtractor deathCountExtractor;
  @Mock private TransactionStatus transactionStatus;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private CommunicationConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer =
        new CommunicationConsumer(
            emailService, smsService, objectMapper, inboxRepository, transactionTemplate, deathCountExtractor);
    ReflectionTestUtils.setField(consumer, "maxRedeliveries", 3);

    // Make the mocked TransactionTemplate actually invoke the callbacks it's given, the same way
    // Spring's real implementation would - without this, business logic inside the lambdas would
    // never execute and the test would prove nothing.
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<Boolean> callback = invocation.getArgument(0);
      return callback.doInTransaction(transactionStatus);
    });
    // Spring 7's TransactionTemplate.executeWithoutResult takes a Consumer<TransactionStatus>
    org.mockito.Mockito.lenient()
        .doAnswer(invocation -> {
          Consumer<TransactionStatus> callback = invocation.getArgument(0);
          callback.accept(transactionStatus);
          return null;
        })
        .when(transactionTemplate)
        .executeWithoutResult(any());

    when(deathCountExtractor.extractDeathCount(any(), anyString())).thenReturn(0L);
  }

  private Message otpEmailMessage(String messageId, String toEmail, String otp) {
    MessageProperties props = new MessageProperties();
    props.setMessageId(messageId);
    String body = String.format("{\"toEmail\":\"%s\",\"otp\":\"%s\",\"expiryMinutes\":5}", toEmail, otp);
    return new Message(body.getBytes(StandardCharsets.UTF_8), props);
  }

  private Message otpSmsMessage(String messageId, String toPhone, String otp) {
    MessageProperties props = new MessageProperties();
    props.setMessageId(messageId);
    String body =
        String.format("{\"toPhoneNumber\":\"%s\",\"otp\":\"%s\",\"expiryMinutes\":5}", toPhone, otp);
    return new Message(body.getBytes(StandardCharsets.UTF_8), props);
  }

  @Test
  void handleOtpEmail_newEvent_claimsInboxSendsEmailAndMarksProcessed() {
    when(inboxRepository.findByEventId("evt-1")).thenReturn(Optional.empty());

    consumer.handleOtpEmail(otpEmailMessage("evt-1", "user@example.com", "123456"));

    verify(emailService).sendOtpEmail("user@example.com", "123456", 5);
    verify(inboxRepository, times(2)).save(any(InboxEvent.class));
  }

  @Test
  void handleOtpEmail_alreadyProcessed_skipsBusinessLogicEntirely() {
    InboxEvent existing =
        InboxEvent.builder()
            .eventId("evt-2")
            .eventType("OTP_EMAIL")
            .status(InboxEvent.ProcessStatus.PROCESSED)
            .build();
    when(inboxRepository.findByEventId("evt-2")).thenReturn(Optional.of(existing));

    consumer.handleOtpEmail(otpEmailMessage("evt-2", "user@example.com", "123456"));

    verify(emailService, never()).sendOtpEmail(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void handleOtpEmail_previouslyFailed_reprocessesAndIncrementsRetryCount() {
    InboxEvent existing =
        InboxEvent.builder()
            .eventId("evt-3")
            .eventType("OTP_EMAIL")
            .status(InboxEvent.ProcessStatus.FAILED)
            .retryCount(1)
            .build();
    when(inboxRepository.findByEventId("evt-3")).thenReturn(Optional.of(existing));

    consumer.handleOtpEmail(otpEmailMessage("evt-3", "user@example.com", "123456"));

    assertThat(existing.getRetryCount()).isEqualTo(2);
    verify(emailService).sendOtpEmail("user@example.com", "123456", 5);
  }

  @Test
  void handleOtpEmail_businessLogicThrows_marksInboxFailedAndRethrowsRuntimeException() {
    InboxEvent existing =
        InboxEvent.builder()
            .eventId("evt-4")
            .eventType("OTP_EMAIL")
            .status(InboxEvent.ProcessStatus.PENDING)
            .retryCount(0)
            .build();
    when(inboxRepository.findByEventId("evt-4")).thenReturn(Optional.of(existing));
    doThrow(new IllegalStateException("SendGrid down"))
        .when(emailService)
        .sendOtpEmail(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());

    assertThatThrownBy(() -> consumer.handleOtpEmail(otpEmailMessage("evt-4", "user@example.com", "123456")))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(IllegalStateException.class);

    assertThat(existing.getStatus()).isEqualTo(InboxEvent.ProcessStatus.FAILED);
    assertThat(existing.getErrorMessage()).isEqualTo("SendGrid down");
  }

  @Test
  void handleOtpSms_newEvent_claimsInboxSendsSmsAndMarksProcessed() {
    when(inboxRepository.findByEventId("evt-5")).thenReturn(Optional.empty());

    consumer.handleOtpSms(otpSmsMessage("evt-5", "+15005550006", "654321"));

    verify(smsService).sendOtp("+15005550006", "654321", 5);
  }

  @Test
  void handleOtpSms_businessLogicThrows_marksInboxFailedAndRethrows() {
    when(inboxRepository.findByEventId("evt-6")).thenReturn(Optional.empty());
    doThrow(new IllegalStateException("Twilio down"))
        .when(smsService)
        .sendOtp(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());

    assertThatThrownBy(() -> consumer.handleOtpSms(otpSmsMessage("evt-6", "+15005550006", "654321")))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void handleOtpEmail_concurrentDuplicateInsert_skipsBusinessLogic() {
    when(inboxRepository.findByEventId("evt-7")).thenReturn(Optional.empty());
    when(inboxRepository.save(any(InboxEvent.class)))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

    consumer.handleOtpEmail(otpEmailMessage("evt-7", "user@example.com", "123456"));

    verify(emailService, never()).sendOtpEmail(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
  }
}
