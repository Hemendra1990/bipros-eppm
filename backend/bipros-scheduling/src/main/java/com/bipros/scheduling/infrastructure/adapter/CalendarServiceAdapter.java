package com.bipros.scheduling.infrastructure.adapter;

import com.bipros.calendar.application.service.CalendarService;
import com.bipros.scheduling.domain.algorithm.CalendarCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CalendarServiceAdapter implements CalendarCalculator {

  private final CalendarService calendarService;

  @Override
  public boolean isWorkingDay(UUID calendarId, LocalDate date) {
    return calendarService.isWorkingDay(calendarId, date);
  }

  @Override
  public double getWorkingHours(UUID calendarId, LocalDate date) {
    return calendarService.getWorkingHours(calendarId, date);
  }

  @Override
  public LocalDate addWorkingDays(UUID calendarId, LocalDate start, double days) {
    return calendarService.addWorkingDays(calendarId, start, days);
  }

  @Override
  public LocalDate subtractWorkingDays(UUID calendarId, LocalDate from, double days) {
    return calendarService.subtractWorkingDays(calendarId, from, days);
  }

  @Override
  public double countWorkingDays(UUID calendarId, LocalDate start, LocalDate end) {
    return calendarService.countWorkingDays(calendarId, start, end);
  }
}
