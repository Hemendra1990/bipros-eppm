package com.bipros.scheduling.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "schedule_results", schema = "scheduling")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleResult extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "data_date", nullable = false)
  private LocalDate dataDate;

  @Column(name = "project_start_date")
  private LocalDate projectStartDate;

  @Column(name = "project_finish_date")
  private LocalDate projectFinishDate;

  @Column(name = "critical_path_length")
  private Double criticalPathLength;

  @Column(name = "total_activities")
  private int totalActivities;

  @Column(name = "critical_activities")
  private int criticalActivities;

  @Column(name = "scheduling_option")
  @Enumerated(EnumType.STRING)
  private SchedulingOption schedulingOption;

  @Column(name = "calculated_at", nullable = false)
  private Instant calculatedAt;

  @Column(name = "duration_seconds")
  private Double durationSeconds;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private ScheduleStatus status;
}
