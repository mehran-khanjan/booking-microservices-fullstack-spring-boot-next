package com.example.commonlib.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

/**
 * AOP aspect that provides idempotency for methods annotated with {@link Idempotent}.
 *
 * <p>Uses Redis to store a lock and the cached response for the given idempotency key, ensuring
 * that duplicate requests with the same key are idempotent.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

  private final RedisTemplate<String, String> redisTemplate;
  private final ObjectMapper objectMapper;

  @Around("@annotation(idempotent)")
  public Object handle(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {

    HttpServletRequest request =
        ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

    String idempotencyKey = request.getHeader("Idempotency-Key");

    if (!StringUtils.hasText(idempotencyKey)) {
      //            throw new MissingIdempotencyKeyException("Idempotency-Key header is required");

      throw new Exception("Idempotency-Key header is required");
    }

    String redisKey = "idempotency:" + request.getRequestURI() + ":" + idempotencyKey;

    // Atomic check-and-set — prevents race condition on concurrent duplicate requests
    Boolean acquired =
        redisTemplate
            .opsForValue()
            .setIfAbsent(redisKey, "PROCESSING", Duration.ofSeconds(idempotent.ttlSeconds()));

    if (Boolean.FALSE.equals(acquired)) {
      String cachedResult = redisTemplate.opsForValue().get(redisKey);

      if ("PROCESSING".equals(cachedResult)) {
        log.warn("Duplicate in-flight request detected for idempotencyKey={}", idempotencyKey);
        //                throw new DuplicateRequestInFlightException("Request already being
        // processed");
        throw new Exception("Request already being processed");
      }

      log.info("Returning cached idempotent response for key={}", idempotencyKey);
      return objectMapper.readValue(
          cachedResult,
          pjp.getSignature() instanceof MethodSignature ms ? ms.getReturnType() : Object.class);
    }

    try {
      Object result = pjp.proceed();
      redisTemplate
          .opsForValue()
          .set(
              redisKey,
              objectMapper.writeValueAsString(result),
              Duration.ofSeconds(idempotent.ttlSeconds()));
      return result;
    } catch (Exception ex) {
      redisTemplate.delete(redisKey); // allow retry on genuine failure
      throw ex;
    }
  }
}

// Usage

/**
 * @PostMapping("/bookings") @Idempotent(ttlSeconds = 3600) public ResponseEntity<BookingResponse>
 * createBooking(@RequestBody @Valid BookingRequest request) { return
 * ResponseEntity.ok(orderService.createBooking(request)); }
 */
