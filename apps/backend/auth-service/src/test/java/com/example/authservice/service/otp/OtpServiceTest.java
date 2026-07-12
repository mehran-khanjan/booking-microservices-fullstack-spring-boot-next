package com.example.authservice.service.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.authservice.dto.otp.OtpRedisRecord;
import com.example.authservice.dto.otp.OtpRequest;
import com.example.authservice.enums.OtpChannel;
import com.example.commonlib.exception.InvalidOtpException;
import com.example.commonlib.exception.TooManyOtpAttemptsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private EmailService emailService;
  @Mock private SmsService smsService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private OtpService otpService;

  @BeforeEach
  void setUp() {
    otpService = new OtpService(redisTemplate, objectMapper, emailService, smsService);
    ReflectionTestUtils.setField(otpService, "otpLength", 6);
    ReflectionTestUtils.setField(otpService, "otpExpiryMinutes", 5L);
    ReflectionTestUtils.setField(otpService, "maxAttempts", 5);
  }

  @Test
  void generateAndSendOtp_emailChannel_persistsRecord_andDispatchesEmail() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    otpService.generateAndSendOtp(new OtpRequest(OtpChannel.EMAIL, "user@example.com", "user-id"));

    verify(valueOperations)
        .set(eq("otp:email:user@example.com"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    verify(emailService).sendOtpEmail(eq("user@example.com"), anyString());
    verify(smsService, never()).sendOtp(anyString(), anyString());
  }

  @Test
  void generateAndSendOtp_phoneChannel_dispatchesSms() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    otpService.generateAndSendOtp(new OtpRequest(OtpChannel.PHONE, "+15551234567", "user-id"));

    verify(valueOperations)
        .set(eq("otp:phone:+15551234567"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    verify(smsService).sendOtp(eq("+15551234567"), anyString());
    verify(emailService, never()).sendOtpEmail(anyString(), anyString());
  }

  @Test
  void verifyOtp_correctOtp_deletesKey_andReturnsUserId() throws Exception {
    OtpRedisRecord record = new OtpRedisRecord("123456", "user-id", 0);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("otp:email:user@example.com"))
        .thenReturn(objectMapper.writeValueAsString(record));

    String userId = otpService.verifyOtp(OtpChannel.EMAIL, "user@example.com", "123456");

    assertThat(userId).isEqualTo("user-id");
    verify(redisTemplate).delete("otp:email:user@example.com");
  }

  @Test
  void verifyOtp_missingOrExpired_throwsInvalidOtpException() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("otp:email:user@example.com")).thenReturn(null);

    assertThatThrownBy(() -> otpService.verifyOtp(OtpChannel.EMAIL, "user@example.com", "123456"))
        .isInstanceOf(InvalidOtpException.class);
  }

  @Test
  void verifyOtp_wrongOtp_incrementsAttempts_andThrowsInvalidOtpException() throws Exception {
    OtpRedisRecord record = new OtpRedisRecord("123456", "user-id", 0);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("otp:email:user@example.com"))
        .thenReturn(objectMapper.writeValueAsString(record));
    when(redisTemplate.getExpire("otp:email:user@example.com", TimeUnit.SECONDS)).thenReturn(120L);

    assertThatThrownBy(() -> otpService.verifyOtp(OtpChannel.EMAIL, "user@example.com", "000000"))
        .isInstanceOf(InvalidOtpException.class);

    verify(valueOperations).set(eq("otp:email:user@example.com"), anyString(), eq(120L), eq(TimeUnit.SECONDS));
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  void verifyOtp_maxAttemptsExceeded_deletesKey_andThrowsTooManyAttempts() throws Exception {
    OtpRedisRecord record = new OtpRedisRecord("123456", "user-id", 5);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("otp:email:user@example.com"))
        .thenReturn(objectMapper.writeValueAsString(record));

    assertThatThrownBy(() -> otpService.verifyOtp(OtpChannel.EMAIL, "user@example.com", "123456"))
        .isInstanceOf(TooManyOtpAttemptsException.class);

    verify(redisTemplate).delete("otp:email:user@example.com");
  }
}
