package com.example.outboxlib.outbox.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * JPA attribute converter that transforms {@link LocalDateTime} to and from epoch milliseconds (as
 * a {@link Long}) in UTC time zone.
 *
 * <p>This converter is used to store date-time values as numeric timestamps in the database, which
 * is time-zone neutral and easy to compare.
 *
 * <p><b>Note:</b> This converter does not apply automatically; it must be explicitly specified via
 * {@code @Convert(converter = EpochMillisConverter.class)}.
 *
 * @see jakarta.persistence.Convert
 */
@Converter(autoApply = false)
public class EpochMillisConverter implements AttributeConverter<LocalDateTime, Long> {

  /**
   * Converts a {@link LocalDateTime} to epoch milliseconds.
   *
   * @param attribute the {@code LocalDateTime} to convert
   * @return the number of milliseconds since the epoch (1970-01-01T00:00:00Z), or {@code null} if
   *     the input is {@code null}
   */
  @Override
  public Long convertToDatabaseColumn(LocalDateTime attribute) {
    return attribute == null ? null : attribute.toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  /**
   * Converts epoch milliseconds back to a {@link LocalDateTime}.
   *
   * @param dbData the epoch millisecond value from the database
   * @return a {@code LocalDateTime} representing the same instant in UTC, or {@code null} if the
   *     input is {@code null}
   */
  @Override
  public LocalDateTime convertToEntityAttribute(Long dbData) {
    return dbData == null
        ? null
        : LocalDateTime.ofInstant(Instant.ofEpochMilli(dbData), ZoneOffset.UTC);
  }
}
