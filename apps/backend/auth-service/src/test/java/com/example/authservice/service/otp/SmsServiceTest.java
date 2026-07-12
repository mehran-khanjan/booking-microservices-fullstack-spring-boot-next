package com.example.authservice.service.otp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.example.commonlib.event.CommunicationRoutingKeys;
import com.example.commonlib.event.OtpSmsEvent;
import com.example.outboxlib.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

  @Mock private OutboxService outboxService;

  private SmsService smsService;

  @BeforeEach
  void setUp() {
    smsService = new SmsService(outboxService);
    ReflectionTestUtils.setField(smsService, "otpExpiryMinutes", 5);
  }

  @Test
  void sendOtp_queuesEventViaOutbox_withCorrectRoutingInfo() {
    smsService.sendOtp("+15551234567", "654321");

    verify(outboxService)
        .saveEvent(
            any(OtpSmsEvent.class),
            eq("OTP_SMS"),
            eq(CommunicationRoutingKeys.EXCHANGE),
            eq(CommunicationRoutingKeys.OTP_SMS),
            eq("User"),
            eq("+15551234567"));
  }
}
