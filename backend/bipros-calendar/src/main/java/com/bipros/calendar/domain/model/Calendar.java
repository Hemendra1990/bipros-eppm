package com.bipros.calendar.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
    name = "calendars",
    schema = "scheduling",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_calendar_name_type_project",
            columnNames = {"name", "calendar_type", "project_id"})
    },
    indexes = {
        @Index(name = "idx_calendar_type", columnList = "calendar_type"),
        @Index(name = "idx_calendar_project_id", columnList = "project_id"),
        @Index(name = "idx_calendar_resource_id", columnList = "resource_id"),
        @Index(name = "idx_calendar_is_default", columnList = "is_default")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Calendar extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "calendar_type", nullable = false, length = 20)
  private CalendarType calendarType;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "parent_calendar_id")
  private UUID parentCalendarId;

  @Column(name = "is_default", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  @Default
  private Boolean isDefault = false;

  @Column(name = "standard_work_hours_per_day", nullable = false, columnDefinition = "double precision default 8.0")
  @Default
  private Double standardWorkHoursPerDay = 8.0;

  @Column(name = "standard_work_days_per_week", nullable = false, columnDefinition = "INTEGER DEFAULT 5")
  @Default
  private Integer standardWorkDaysPerWeek = 5;
}
