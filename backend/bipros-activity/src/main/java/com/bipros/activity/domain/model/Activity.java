package com.bipros.activity.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "activities", schema = "activity", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "code"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Activity extends BaseEntity {

  @Column(nullable = false, length = 20)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "wbs_node_id", nullable = false)
  private UUID wbsNodeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ActivityType activityType = ActivityType.TASK_DEPENDENT;

  @Enumerated(EnumType.STRING)
  @Column(name = "duration_type")
  private DurationType durationType = DurationType.FIXED_DURATION_AND_UNITS;

  @Enumerated(EnumType.STRING)
  @Column(name = "percent_complete_type")
  private PercentCompleteType percentCompleteType = PercentCompleteType.DURATION;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ActivityStatus status = ActivityStatus.NOT_STARTED;

  @Column(name = "original_duration")
  private Double originalDuration;

  @Column(name = "remaining_duration")
  private Double remainingDuration;

  @Column(name = "at_completion_duration")
  private Double atCompletionDuration;

  @Column(name = "planned_start_date")
  private LocalDate plannedStartDate;

  @Column(name = "planned_finish_date")
  private LocalDate plannedFinishDate;

  @Column(name = "early_start_date")
  private LocalDate earlyStartDate;

  @Column(name = "early_finish_date")
  private LocalDate earlyFinishDate;

  @Column(name = "late_start_date")
  private LocalDate lateStartDate;

  @Column(name = "late_finish_date")
  private LocalDate lateFinishDate;

  @Column(name = "actual_start_date")
  private LocalDate actualStartDate;

  @Column(name = "actual_finish_date")
  private LocalDate actualFinishDate;

  @Column(name = "total_float")
  private Double totalFloat;

  @Column(name = "free_float")
  private Double freeFloat;

  @Column(nullable = false)
  private Double percentComplete = 0.0;

  @Column(name = "physical_percent_complete")
  private Double physicalPercentComplete;

  @Column(name = "duration_percent_complete")
  private Double durationPercentComplete;

  @Column(name = "units_percent_complete")
  private Double unitsPercentComplete;

  @Column(name = "calendar_id")
  private UUID calendarId;

  @Column(name = "is_critical", nullable = false)
  private Boolean isCritical = false;

  @Column(name = "suspend_date")
  private LocalDate suspendDate;

  @Column(name = "resume_date")
  private LocalDate resumeDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "primary_constraint_type")
  private ConstraintType primaryConstraintType;

  @Column(name = "primary_constraint_date")
  private LocalDate primaryConstraintDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "secondary_constraint_type")
  private ConstraintType secondaryConstraintType;

  @Column(name = "secondary_constraint_date")
  private LocalDate secondaryConstraintDate;

  @Column(name = "sort_order")
  private Integer sortOrder;
}
