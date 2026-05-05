package com.bipros.baseline.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Phase 5.2: snapshot of a {@code ResourceAssignment} captured at baseline creation time.
 * Lets the variance dashboard answer "what was originally planned for THIS resource on THIS
 * activity?" — impossible today because {@code BaselineActivity.plannedCost} is only the
 * activity-level rollup.
 */
@Entity
@Table(
    name = "baseline_resource_assignments",
    schema = "baseline",
    indexes = {
        @Index(name = "idx_bra_baseline_id", columnList = "baseline_id"),
        @Index(name = "idx_bra_activity_id", columnList = "activity_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaselineResourceAssignment extends BaseEntity {

  @Column(name = "baseline_id", nullable = false)
  private UUID baselineId;

  /** Original resource_assignments.id — soft FK; the row may have been deleted post-snapshot. */
  @Column(name = "assignment_id", nullable = false)
  private UUID assignmentId;

  @Column(name = "activity_id", nullable = false)
  private UUID activityId;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "role_id")
  private UUID roleId;

  @Column(name = "budgeted_units")
  private Double budgetedUnits;

  @Column(name = "budgeted_cost", precision = 19, scale = 4)
  private BigDecimal budgetedCost;

  @Column(name = "planned_units")
  private Double plannedUnits;

  @Column(name = "planned_cost", precision = 19, scale = 4)
  private BigDecimal plannedCost;

  @Column(name = "rate_type", length = 50)
  private String rateType;
}
