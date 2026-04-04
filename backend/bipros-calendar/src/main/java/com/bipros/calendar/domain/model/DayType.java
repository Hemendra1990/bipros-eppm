package com.bipros.calendar.domain.model;

/**
 * Enumeration of day types in a calendar.
 * - WORKING: Standard working day (follows work week pattern)
 * - NON_WORKING: Non-working day (holiday, weekend)
 * - EXCEPTION_WORKING: Normally non-working day that is working (makeup day)
 * - EXCEPTION_NON_WORKING: Normally working day that is non-working (holiday)
 */
public enum DayType {
  WORKING,
  NON_WORKING,
  EXCEPTION_WORKING,
  EXCEPTION_NON_WORKING
}
