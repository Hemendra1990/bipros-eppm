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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
    name = "calendar_work_weeks",
    schema = "scheduling",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_calendar_work_week_day",
            columnNames = {"calendar_id", "day_of_week"})
    },
    indexes = {
        @Index(name = "idx_calendar_work_week_calendar_id", columnList = "calendar_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarWorkWeek extends BaseEntity {

  @Column(name = "calendar_id", nullable = false)
  private UUID calendarId;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week", nullable = false, length = 10)
  private DayOfWeek dayOfWeek;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_type", nullable = false, length = 25)
  private DayType dayType;

  @Column(name = "start_time1")
  private LocalTime startTime1;

  @Column(name = "end_time1")
  private LocalTime endTime1;

  @Column(name = "start_time2")
  private LocalTime startTime2;

  @Column(name = "end_time2")
  private LocalTime endTime2;

  @Column(name = "total_work_hours")
  private Double totalWorkHours;
}
