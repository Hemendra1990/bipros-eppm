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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 5.3: snapshot of an {@code ActivityExpense} captured at baseline creation time. Lets
 * the variance dashboard show direct-expense-level deltas (e.g. "permit fee was budgeted at
 * ₹50k but came in at ₹62k").
 */
@Entity
@Table(
    name = "baseline_expenses",
    schema = "baseline",
    indexes = {
        @Index(name = "idx_be_baseline_id", columnList = "baseline_id"),
        @Index(name = "idx_be_activity_id", columnList = "activity_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaselineExpense extends BaseEntity {

  @Column(name = "baseline_id", nullable = false)
  private UUID baselineId;

  /** Original activity_expenses.id — soft FK. */
  @Column(name = "expense_id", nullable = false)
  private UUID expenseId;

  @Column(name = "activity_id")
  private UUID activityId;

  @Column(length = 200)
  private String name;

  @Column(name = "expense_category", length = 100)
  private String expenseCategory;

  @Column(name = "budgeted_cost", precision = 19, scale = 2)
  private BigDecimal budgetedCost;

  @Column(name = "planned_start_date")
  private LocalDate plannedStartDate;

  @Column(name = "planned_finish_date")
  private LocalDate plannedFinishDate;
}
