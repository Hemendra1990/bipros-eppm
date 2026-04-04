package com.bipros.calendar.domain.repository;

import com.bipros.calendar.domain.model.CalendarWorkWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarWorkWeekRepository extends JpaRepository<CalendarWorkWeek, UUID> {

  List<CalendarWorkWeek> findByCalendarId(UUID calendarId);

  Optional<CalendarWorkWeek> findByCalendarIdAndDayOfWeek(UUID calendarId, DayOfWeek dayOfWeek);

  void deleteByCalendarId(UUID calendarId);
}
