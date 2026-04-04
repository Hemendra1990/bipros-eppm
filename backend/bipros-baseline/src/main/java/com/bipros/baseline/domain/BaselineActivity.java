package com.bipros.baseline.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "baseline_activities",
    schema = "baseline",
    uniqueConstraints = @UniqueConstraint(columnNames = {"baseline_id", "activity_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BaselineActivity extends BaseEntity {

  @Column(nullable = false)
  private UUID baselineId;

  @Column(nullable = false)
  private UUID activityId;

  @Column
  private LocalDate earlyStart;

  @Column
  private LocalDate earlyFinish;

  @Column
  private LocalDate lateStart;

  @Column
  private LocalDate lateFinish;

  @Column
  private Double originalDuration;

  @Column
  private Double remainingDuration;

  @Column
  private Double totalFloat;

  @Column
  private Double freeFloat;

  @Column
  private BigDecimal plannedCost;

  @Column
  private BigDecimal actualCost;

  @Column
  private Double percentComplete;
}
