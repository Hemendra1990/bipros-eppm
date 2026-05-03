package com.bipros.calendar.application.service;

import com.bipros.calendar.domain.model.CalendarException;
import com.bipros.calendar.domain.model.CalendarWorkWeek;
import com.bipros.calendar.domain.model.DayType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable in-memory view of a calendar's work-week pattern and exceptions for a date range.
 * Supports zero-DB working-day calculations — the right tool when a single request needs to
 * evaluate {@code isWorkingDay} or {@code countWorkingDays} many times (e.g. the resource-usage
 * time-phased report, which spreads hundreds of assignments across project-long ranges).
 *
 * <p>Construct via {@link CalendarService#loadSnapshot(UUID, LocalDate, LocalDate)}. The snapshot
 * holds at most 7 work-week rows and one exception row per holiday/override day in the loaded
 * window — no matter how many days you query against it.
 *
 * <p><b>Range semantics:</b> exceptions outside the loaded {@code [from, to]} window are NOT in
 * the snapshot, so queries against dates outside that window fall through to the regular work-week
 * pattern (no holiday override). Callers are expected to load with a range that covers their
 * intended queries.
 */
public final class CalendarSnapshot {
  private final UUID calendarId;
  private final Map<DayOfWeek, CalendarWorkWeek> workWeekByDay;
  private final Map<LocalDate, CalendarException> exceptionByDate;

  public CalendarSnapshot(
      UUID calendarId,
      Map<DayOfWeek, CalendarWorkWeek> workWeekByDay,
      Map<LocalDate, CalendarException> exceptionByDate) {
    this.calendarId = calendarId;
    this.workWeekByDay = workWeekByDay;
    this.exceptionByDate = exceptionByDate;
  }

  public UUID calendarId() {
    return calendarId;
  }

  /** Mirrors {@link CalendarService#isWorkingDay(UUID, LocalDate)} but reads from memory. */
  public boolean isWorkingDay(LocalDate date) {
    CalendarException ex = exceptionByDate.get(date);
    if (ex != null) {
      return ex.getDayType() == DayType.EXCEPTION_WORKING;
    }
    CalendarWorkWeek ww = workWeekByDay.get(date.getDayOfWeek());
    return ww != null && ww.getDayType() == DayType.WORKING;
  }

  /**
   * Mirrors {@link CalendarService#countWorkingDays(UUID, LocalDate, LocalDate)}. Half-open
   * {@code [start, end)} — same-day returns 0.
   */
  public double countWorkingDays(LocalDate start, LocalDate end) {
    if (!start.isBefore(end)) {
      return 0.0;
    }
    double count = 0;
    for (LocalDate d = start; d.isBefore(end); d = d.plusDays(1)) {
      if (isWorkingDay(d)) {
        count++;
      }
    }
    return count;
  }
}
