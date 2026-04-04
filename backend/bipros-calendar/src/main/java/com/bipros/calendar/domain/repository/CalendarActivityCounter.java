package com.bipros.calendar.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Repository for counting activities from the activity schema.
 * Uses EntityManager with native queries to avoid circular dependency between modules.
 */
@Component
public class CalendarActivityCounter {

  @PersistenceContext
  private EntityManager entityManager;

  public long countActivitiesByCalendarId(UUID calendarId) {
    return ((Number) entityManager
        .createNativeQuery("SELECT COUNT(*) FROM activity.activities WHERE calendar_id = ?1")
        .setParameter(1, calendarId)
        .getSingleResult()).longValue();
  }
}
