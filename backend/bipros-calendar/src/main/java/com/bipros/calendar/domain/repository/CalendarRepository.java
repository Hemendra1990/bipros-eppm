package com.bipros.calendar.domain.repository;

import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.model.CalendarType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarRepository extends JpaRepository<Calendar, UUID> {

  List<Calendar> findByCalendarType(CalendarType calendarType);

  List<Calendar> findByProjectId(UUID projectId);

  List<Calendar> findByResourceId(UUID resourceId);

  List<Calendar> findByIsDefaultTrue();

  Optional<Calendar> findByCalendarTypeAndIsDefaultTrue(CalendarType calendarType);
}
