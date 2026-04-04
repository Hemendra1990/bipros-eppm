package com.bipros.calendar.domain.model;

/**
 * Enumeration of calendar types in the system.
 * - GLOBAL: System-wide calendar applicable to all projects
 * - PROJECT: Project-specific calendar
 * - RESOURCE: Resource-specific calendar (individual worker, machine, etc.)
 */
public enum CalendarType {
  GLOBAL,
  PROJECT,
  RESOURCE
}
