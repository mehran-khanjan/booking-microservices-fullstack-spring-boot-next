package com.example.authservice.service.otp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.commonlib.event.OtpEmailEvent;
import com.example.outboxlib.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

  @Mock private OutboxService outboxService;

  private EmailService emailService;

  @BeforeEach
  void setUp() {
    emailService = new EmailService(outboxService);
    ReflectionTestUtils.setField(emailService, "otpExpiryMinutes", 5);
  }

  @Test
  void sendOtpEmail_queuesEventViaOutbox_withCorrectRoutingInfo() {
    emailService.sendOtpEmail("user@example.com", "123456");

    verify(outboxService)
        .saveEvent(
            any(OtpEmailEvent.class),
            eq("OTP_EMAIL"),
            eq(CommunicationRoutingKeys.EXCHANGE),
            eq(CommunicationRoutingKeys.OTP_EMAIL),
            eq("User"),
            eq("user@example.com"));
  }
}
