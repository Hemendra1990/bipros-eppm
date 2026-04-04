package com.bipros.resource.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "resource_assignments",
    schema = "resource",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_assignment_activity_resource",
            columnNames = {"activity_id", "resource_id"})
    },
    indexes = {
        @Index(name = "idx_assignment_activity_id", columnList = "activity_id"),
        @Index(name = "idx_assignment_resource_id", columnList = "resource_id"),
        @Index(name = "idx_assignment_project_id", columnList = "project_id"),
        @Index(name = "idx_assignment_planned_start_date", columnList = "planned_start_date"),
        @Index(name = "idx_assignment_planned_finish_date", columnList = "planned_finish_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceAssignment extends BaseEntity {

  @Column(name = "activity_id", nullable = false)
  private UUID activityId;

  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "role_id")
  private UUID roleId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "planned_units")
  private Double plannedUnits;

  @Column(name = "actual_units")
  private Double actualUnits;

  @Column(name = "remaining_units")
  private Double remainingUnits;

  @Column(name = "at_completion_units")
  private Double atCompletionUnits;

  @Column(name = "planned_cost", precision = 19, scale = 4)
  private BigDecimal plannedCost;

  @Column(name = "actual_cost", precision = 19, scale = 4)
  private BigDecimal actualCost;

  @Column(name = "remaining_cost", precision = 19, scale = 4)
  private BigDecimal remainingCost;

  @Column(name = "at_completion_cost", precision = 19, scale = 4)
  private BigDecimal atCompletionCost;

  @Column(name = "rate_type", length = 50)
  private String rateType;

  @Column(name = "resource_curve_id")
  private UUID resourceCurveId;

  @Column(name = "planned_start_date")
  private LocalDate plannedStartDate;

  @Column(name = "planned_finish_date")
  private LocalDate plannedFinishDate;

  @Column(name = "actual_start_date")
  private LocalDate actualStartDate;

  @Column(name = "actual_finish_date")
  private LocalDate actualFinishDate;
}
