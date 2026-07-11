package com.example.authservice.service.otp;

import static com.example.authservice.util.Util.otpMask;

import com.example.authservice.dto.otp.OtpRedisRecord;
import com.example.authservice.dto.otp.OtpRequest;
import com.example.authservice.enums.OtpChannel;
import com.example.commonlib.exception.InvalidOtpException;
import com.example.commonlib.exception.OtpServiceUnavailableException;
import com.example.commonlib.exception.TooManyOtpAttemptsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Core service for generating, storing, verifying, and dispatching one-time passwords (OTPs).
 *
 * <p>This service manages the entire OTP lifecycle:
 *
 * <ul>
 *   <li><strong>Generation:</strong> Creates a cryptographically secure numeric OTP.
 *   <li><strong>Persistence:</strong> Stores the OTP in Redis with a configurable TTL and attempts
 *       counter.
 *   <li><strong>Dispatch:</strong> Delegates delivery to either {@link EmailService} or {@link
 *       SmsService} based on the requested {@link OtpChannel}.
 *   <li><strong>Verification:</strong> Validates the submitted OTP against the stored value,
 *       enforces max‑attempts limits, and deletes the OTP upon success.
 * </ul>
 *
 * <p>The service is resilient to Redis failures using a circuit breaker, retries, and a bulkhead to
 * limit concurrent dispatch operations. Fallback methods handle failures gracefully, distinguishing
 * between business exceptions (which are rethrown) and infrastructure failures (which result in a
 * user‑friendly {@link OtpServiceUnavailableException}).
 *
 * <p>All OTP operations are atomic and transactional where applicable. The Redis key format is
 * {@code <channel-prefix>:<identifier>} (e.g., {@code otp:email:user@example.com}).
 *
 * @author Your Team
 * @see OtpChannel
 * @see OtpRedisRecord
 * @see EmailService
 * @see SmsService
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {

  /** Redis template for storing and retrieving OTP records. */
  private final RedisTemplate<String, String> redisTemplate;

  /** Jackson object mapper for serializing/deserializing {@link OtpRedisRecord}. */
  private final ObjectMapper objectMapper;

  /** Service that queues email OTP events via the outbox. */
  private final EmailService emailService;

  /** Service that queues SMS OTP events via the outbox. */
  private final SmsService smsService;

  /** Secure random generator for OTP values. */
  private final SecureRandom secureRandom = new SecureRandom();

  /** Length of the OTP numeric string (default: 6). */
  @Value("${otp.length:6}")
  private int otpLength;

  /** Expiry duration of the OTP in minutes (default: 5). */
  @Value("${otp.expiry-minutes:5}")
  private long otpExpiryMinutes;

  /** Maximum number of verification attempts allowed (default: 5). */
  @Value("${otp.max-attempts:5}")
  private int maxAttempts;

  /**
   * Generates a new OTP, stores it in Redis, and dispatches it via the requested channel.
   *
   * <p>This method is <strong>transactional</strong> and must be called from within a transaction
   * (e.g., from {@code AuthService}). It creates a fresh OTP record associated with the given user
   * ID and identifier (email or phone), overriding any previous pending OTP for the same
   * identifier. The record includes the OTP value, user ID, and an attempt counter initialised to
   * 0.
   *
   * <p>The OTP is dispatched synchronously via the appropriate channel service (email or SMS),
   * which in turn places an event in the outbox for reliable asynchronous delivery.
   *
   * <p>Resilience patterns applied:
   *
   * <ul>
   *   <li>{@link CircuitBreaker} – prevents repeated calls when Redis is failing
   *   <li>{@link Retry} – retries transient Redis errors
   *   <li>{@link Bulkhead} – limits concurrent OTP dispatches to avoid resource exhaustion
   * </ul>
   *
   * @param otpRequest the request containing the channel, identifier (email/phone), and user ID
   * @throws OtpServiceUnavailableException if Redis or the dispatch fails (fallback)
   * @throws IllegalArgumentException if the request or its fields are invalid (fallback rethrows)
   */
  @Transactional
  @CircuitBreaker(name = "redis", fallbackMethod = "generateAndSendOtpFallback")
  @Retry(name = "redis")
  @Bulkhead(name = "otpDispatch")
  public void generateAndSendOtp(OtpRequest otpRequest) {

    String otp = generateOtp();
    OtpRedisRecord record = new OtpRedisRecord(otp, otpRequest.userId(), 0);

    redisTemplate
        .opsForValue()
        .set(
            buildKey(otpRequest.channel(), otpRequest.identifier()),
            serialize(record),
            otpExpiryMinutes,
            TimeUnit.MINUTES);

    dispatch(otpRequest.channel(), otpRequest.identifier(), otp);

    log.info(
        "OTP generated channel={} identifier={} expiresInMin={}",
        otpRequest.channel(),
        otpMask(otpRequest.channel(), otpRequest.identifier()),
        otpExpiryMinutes);
  }

  /**
   * Fallback method for {@link #generateAndSendOtp(OtpRequest)} when the circuit breaker is open or
   * retries are exhausted.
   *
   * @param otpRequest the original request
   * @param t the exception that triggered the fallback
   * @throws IllegalArgumentException if the original exception was of that type (rethrow)
   * @throws OtpServiceUnavailableException for all other failures
   */
  private void generateAndSendOtpFallback(OtpRequest otpRequest, Throwable t) {
    log.error("event=otp_generate_circuit_open channel={}", otpRequest.channel(), t);
    if (t instanceof IllegalArgumentException) {
      throw (IllegalArgumentException) t;
    }
    throw new OtpServiceUnavailableException(
        "OTP service temporarily unavailable. Please try again shortly.");
  }

  /**
   * Verifies a submitted OTP against the stored value for the given channel and identifier.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Retrieves the OTP record from Redis
   *   <li>Rejects if the record is missing (expired or never existed)
   *   <li>Rejects if the attempt count has reached the maximum allowed
   *   <li>Rejects if the submitted OTP does not match the stored value – increments attempts
   *   <li>On successful match, deletes the OTP from Redis (one‑time use) and returns the user ID
   * </ul>
   *
   * <p>The method is protected by a circuit breaker and retry; the fallback distinguishes between
   * business exceptions (rethrows) and infrastructure failures (throws {@link
   * OtpServiceUnavailableException}).
   *
   * @param channel the delivery channel (EMAIL or PHONE)
   * @param identifier the email address or phone number
   * @param submittedOtp the OTP code provided by the user
   * @return the user ID associated with this OTP
   * @throws InvalidOtpException if the OTP is expired, not found, or does not match
   * @throws TooManyOtpAttemptsException if attempts exceed the maximum
   * @throws OtpServiceUnavailableException if Redis is unavailable (fallback)
   */
  @CircuitBreaker(name = "redis", fallbackMethod = "verifyOtpFallback")
  @Retry(name = "redis")
  public String verifyOtp(OtpChannel channel, String identifier, String submittedOtp) {

    String key = buildKey(channel, identifier);
    String raw = redisTemplate.opsForValue().get(key);

    if (raw == null) {
      log.warn(
          "OTP missing/expired channel={} identifier={}", channel, otpMask(channel, identifier));
      throw new InvalidOtpException("OTP expired or not found. Please request a new one.");
    }

    OtpRedisRecord record = deserialize(raw);

    if (record.attempts() >= maxAttempts) {
      redisTemplate.delete(key);
      log.warn(
          "Max OTP attempts exceeded channel={} identifier={}",
          channel,
          otpMask(channel, identifier));
      throw new TooManyOtpAttemptsException("Maximum attempts exceeded. Please request a new OTP.");
    }

    if (!record.otp().equals(submittedOtp)) {
      persistWithSameTtl(key, record.withIncrementedAttempts());
      log.warn(
          "Invalid OTP attempt={}/{} channel={} identifier={}",
          record.attempts() + 1,
          maxAttempts,
          channel,
          otpMask(channel, identifier));
      throw new InvalidOtpException("Invalid OTP.");
    }

    redisTemplate.delete(key); // one-time use
    log.info(
        "OTP verified channel={} identifier={} userId={}",
        channel,
        otpMask(channel, identifier),
        record.userId());
    return record.userId();
  }

  /**
   * Fallback method for {@link #verifyOtp(OtpChannel, String, String)}.
   *
   * <p>Rethrows business exceptions as-is; wraps all other failures in {@link
   * OtpServiceUnavailableException}.
   *
   * @param channel the channel
   * @param identifier the identifier
   * @param submittedOtp the submitted OTP
   * @param t the cause
   * @return never returns normally
   * @throws RuntimeException (either business exception or {@code OtpServiceUnavailableException})
   */
  private String verifyOtpFallback(
      OtpChannel channel, String identifier, String submittedOtp, Throwable t) {
    log.error("event=otp_verify_circuit_open channel={}", channel, t);

    // Rethrow business exceptions exactly as they were
    if (t instanceof InvalidOtpException
        || t instanceof TooManyOtpAttemptsException
        || t instanceof IllegalArgumentException) {
      log.warn("event=otp_verify_circuit_rethrow exception={}", t.getClass().getSimpleName());
      throw (RuntimeException) t;
    }

    throw new OtpServiceUnavailableException(
        "OTP service temporarily unavailable. Please try again shortly.");
  }

  /**
   * Persists an updated OTP record with the same TTL as the existing key.
   *
   * <p>Used to increment the attempt counter while keeping the original expiration. If the key has
   * no remaining TTL (should not happen), a default TTL is applied.
   *
   * @param key the Redis key
   * @param record the updated record
   */
  private void persistWithSameTtl(String key, OtpRedisRecord record) {
    long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
    redisTemplate
        .opsForValue()
        .set(key, serialize(record), ttl > 0 ? ttl : otpExpiryMinutes * 60, TimeUnit.SECONDS);
  }

  /**
   * Dispatches the OTP via the appropriate channel service.
   *
   * @param channel the channel (EMAIL or PHONE)
   * @param identifier the recipient address/number
   * @param otp the OTP code
   */
  private void dispatch(OtpChannel channel, String identifier, String otp) {
    switch (channel) {
      case EMAIL -> emailService.sendOtpEmail(identifier, otp);
      case PHONE -> smsService.sendOtp(identifier, otp);
    }
  }

  /**
   * Builds the Redis key for an OTP record.
   *
   * @param channel the channel (provides the prefix)
   * @param identifier the email or phone
   * @return the full Redis key string
   */
  private String buildKey(OtpChannel channel, String identifier) {
    return channel.prefix() + identifier;
  }

  /**
   * Generates a numeric OTP of the configured length using a secure random generator.
   *
   * @return a string of digits (e.g., "123456")
   */
  private String generateOtp() {
    int bound = (int) Math.pow(10, otpLength);
    return String.format("%0" + otpLength + "d", secureRandom.nextInt(bound));
  }

  /**
   * Serializes an {@link OtpRedisRecord} to JSON.
   *
   * @param record the record to serialize
   * @return the JSON string
   * @throws RuntimeException if serialization fails (wrapped in SneakyThrows)
   */
  @SneakyThrows
  private String serialize(OtpRedisRecord record) {
    return objectMapper.writeValueAsString(record);
  }

  /**
   * Deserializes a JSON string back to an {@link OtpRedisRecord}.
   *
   * @param raw the JSON string
   * @return the deserialized record
   * @throws RuntimeException if deserialization fails (wrapped in SneakyThrows)
   */
  @SneakyThrows
  private OtpRedisRecord deserialize(String raw) {
    return objectMapper.readValue(raw, OtpRedisRecord.class);
  }
}
