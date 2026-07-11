package com.example.authservice.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for managing revocation of refresh tokens (and optionally access tokens).
 *
 * <p>This service stores revoked tokens in Redis with a TTL equal to the token's configured
 * expiration time. When a token is revoked, it is added to a blacklist; subsequent checks {@link
 * #isTokenRevoked(String)} will return {@code true} for that token until the entry expires.
 *
 * <p>Token revocation is typically triggered during logout or when a refresh token is rotated. The
 * service uses a hashed version of the token (first 32 characters) as the Redis key to keep keys
 * short and consistent.
 *
 * <p>Configuration:
 *
 * <ul>
 *   <li>{@code token.refresh.expiration} – the expiration time of refresh tokens in seconds
 *       (default: 86400, i.e., 24 hours). This is used as the TTL for revoked entries.
 * </ul>
 *
 * <p>The service is not protected with circuit breakers because it is called during authentication
 * flows where Redis availability is already handled by upstream resilience patterns. If Redis
 * fails, the service will throw exceptions that are caught and handled elsewhere.
 *
 * @author Your Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenRevocationService {

  /** Redis template for storing revoked token flags. */
  private final RedisTemplate<String, String> redisTemplate;

  /** Expiration time of refresh tokens in seconds (default: 86400). */
  @Value("${token.refresh.expiration:86400}")
  private long refreshTokenExpiry;

  /** Redis key prefix for revoked tokens. */
  private static final String REVOKED_TOKEN_PREFIX = "revoked:token:";

  /**
   * Revokes a token by adding it to the Redis blacklist.
   *
   * <p>The token is hashed (shortened) to create the key, and the value is set to {@code "revoked"}
   * with a TTL equal to {@code refreshTokenExpiry}. This ensures the entry automatically expires
   * when the token would naturally expire.
   *
   * @param token the token to revoke (must not be {@code null})
   */
  public void revokeToken(String token) {
    String key = REVOKED_TOKEN_PREFIX + hashToken(token);
    redisTemplate.opsForValue().set(key, "revoked", refreshTokenExpiry, TimeUnit.SECONDS);
    log.info("✅ Token revoked (expires in {} seconds)", refreshTokenExpiry);
  }

  /**
   * Checks whether a token has been revoked.
   *
   * @param token the token to check (must not be {@code null})
   * @return {@code true} if the token is in the revocation blacklist, otherwise {@code false}
   */
  public boolean isTokenRevoked(String token) {
    String key = REVOKED_TOKEN_PREFIX + hashToken(token);
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
  }

  /**
   * Hashes the token to a shorter, consistent string for use as a Redis key.
   *
   * <p>Currently takes the first 32 characters of the token. This is sufficient for uniqueness
   * while keeping keys small. If the token is shorter than 32 characters, the whole token is used.
   *
   * @param token the token string
   * @return the hashed (shortened) key
   */
  private String hashToken(String token) {
    // Use first 32 chars or hash for efficiency
    return token.length() > 32 ? token.substring(0, 32) : token;
  }
}
