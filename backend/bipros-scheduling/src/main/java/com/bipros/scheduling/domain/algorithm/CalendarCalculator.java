package com.bipros.scheduling.domain.algorithm;

import java.time.LocalDate;
import java.util.UUID;

public interface CalendarCalculator {

  boolean isWorkingDay(UUID calendarId, LocalDate date);

  double getWorkingHours(UUID calendarId, LocalDate date);

  LocalDate addWorkingDays(UUID calendarId, LocalDate start, double days);

  LocalDate subtractWorkingDays(UUID calendarId, LocalDate from, double days);

  double countWorkingDays(UUID calendarId, LocalDate start, LocalDate end);
}
