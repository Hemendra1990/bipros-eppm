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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
    name = "calendar_exceptions",
    schema = "scheduling",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_calendar_exception_date",
            columnNames = {"calendar_id", "exception_date"})
    },
    indexes = {
        @Index(name = "idx_calendar_exception_calendar_id", columnList = "calendar_id"),
        @Index(name = "idx_calendar_exception_date", columnList = "exception_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarException extends BaseEntity {

  @Column(name = "calendar_id", nullable = false)
  private UUID calendarId;

  @Column(name = "exception_date", nullable = false)
  private LocalDate exceptionDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_type", nullable = false, length = 25)
  private DayType dayType;

  @Column(name = "name", length = 200)
  private String name;

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
