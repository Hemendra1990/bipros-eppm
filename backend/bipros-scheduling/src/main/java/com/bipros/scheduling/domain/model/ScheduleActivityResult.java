package com.bipros.scheduling.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "schedule_activity_results",
    schema = "scheduling",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"schedule_result_id", "activity_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleActivityResult extends BaseEntity {

  @Column(name = "schedule_result_id", nullable = false)
  private UUID scheduleResultId;

  @Column(name = "activity_id", nullable = false)
  private UUID activityId;

  @Column(name = "early_start")
  private LocalDate earlyStart;

  @Column(name = "early_finish")
  private LocalDate earlyFinish;

  @Column(name = "late_start")
  private LocalDate lateStart;

  @Column(name = "late_finish")
  private LocalDate lateFinish;

  @Column(name = "total_float")
  private Double totalFloat;

  @Column(name = "free_float")
  private Double freeFloat;

  @Column(name = "is_critical")
  private Boolean isCritical;

  @Column(name = "remaining_duration")
  private Double remainingDuration;
}
