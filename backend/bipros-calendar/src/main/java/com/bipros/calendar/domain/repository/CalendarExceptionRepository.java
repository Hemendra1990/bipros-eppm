package com.bipros.calendar.domain.repository;

import com.bipros.calendar.domain.model.CalendarException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarExceptionRepository extends JpaRepository<CalendarException, UUID> {

  List<CalendarException> findByCalendarId(UUID calendarId);

  List<CalendarException> findByCalendarIdAndExceptionDateBetween(
      UUID calendarId, LocalDate startDate, LocalDate endDate);

  Optional<CalendarException> findByCalendarIdAndExceptionDate(UUID calendarId, LocalDate exceptionDate);

  void deleteByCalendarId(UUID calendarId);
}
