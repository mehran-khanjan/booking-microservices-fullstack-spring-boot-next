package com.example.commonlib.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method as idempotent. The {@code ttlSeconds} specifies how long the
 * idempotency record (including the cached response) should be retained.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
  long ttlSeconds() default 86400; // 24h
}
