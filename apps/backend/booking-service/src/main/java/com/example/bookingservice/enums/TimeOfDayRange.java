package com.example.bookingservice.enums;

import java.time.LocalTime;

/**
 * Represents a time‑of‑day range with a start and end time. Used for filtering or categorizing
 * flight schedules.
 */
public enum TimeOfDayRange {
  MORNING(LocalTime.of(6, 0), LocalTime.of(12, 0)),
  AFTERNOON(LocalTime.of(12, 0), LocalTime.of(18, 0)),
  EVENING(LocalTime.of(18, 0), LocalTime.of(22, 0)),
  NIGHT(LocalTime.of(22, 0), LocalTime.of(6, 0));

  public final LocalTime start;
  public final LocalTime end;

  TimeOfDayRange(LocalTime start, LocalTime end) {
    this.start = start;
    this.end = end;
  }
}
