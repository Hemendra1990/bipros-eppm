package com.bipros.project.domain.model;

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
 * Supervisor Daily Report — Section D (Next-Day Plan). One row per planned activity the
 * supervisor commits to for the following work day. {@code reportDate} is the date the plan was
 * written; {@code dueDate} is when the action is expected to close.
 */
@Entity
@Table(
    name = "next_day_plans",
    schema = "project",
    indexes = {
        @Index(name = "idx_ndp_project_date", columnList = "project_id, report_date")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NextDayPlan extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "report_date", nullable = false)
  private LocalDate reportDate;

  @Column(name = "next_day_activity", nullable = false, length = 200)
  private String nextDayActivity;

  @Column(name = "chainage_from_m")
  private Long chainageFromM;

  @Column(name = "chainage_to_m")
  private Long chainageToM;

  @Column(name = "target_qty", precision = 18, scale = 3)
  private BigDecimal targetQty;

  @Column(name = "unit", length = 20)
  private String unit;

  @Column(name = "concerns", length = 1000)
  private String concerns;

  @Column(name = "action_by", length = 150)
  private String actionBy;

  @Column(name = "due_date")
  private LocalDate dueDate;
}
