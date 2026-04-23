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
 * Supervisor Daily Progress Report row: one entry per (project, date, chainage range, activity).
 * Captures what was physically executed that day. The service keeps cumulativeQty in step with
 * the sum of prior DPR rows for the same (projectId, activityName) pair, and if
 * {@link #boqItemNo} is set, also syncs qtyExecutedToDate on the matching BoqItem.
 */
@Entity
@Table(
    name = "daily_progress_reports",
    schema = "project",
    indexes = {
        @Index(name = "idx_dpr_project_date", columnList = "project_id, report_date"),
        @Index(name = "idx_dpr_activity", columnList = "project_id, activity_name"),
        @Index(name = "idx_dpr_wbs", columnList = "wbs_node_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyProgressReport extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "report_date", nullable = false)
  private LocalDate reportDate;

  @Column(name = "supervisor_name", nullable = false, length = 150)
  private String supervisorName;

  @Column(name = "chainage_from_m")
  private Long chainageFromM;

  @Column(name = "chainage_to_m")
  private Long chainageToM;

  @Column(name = "activity_name", nullable = false, length = 150)
  private String activityName;

  @Column(name = "wbs_node_id")
  private UUID wbsNodeId;

  /** Optional back-link to the BOQ item — when set, DPR save updates that item's executed qty. */
  @Column(name = "boq_item_no", length = 20)
  private String boqItemNo;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "qty_executed", nullable = false, precision = 18, scale = 3)
  private BigDecimal qtyExecuted;

  /** Running total of qtyExecuted for this (project, activityName) up to & including reportDate. */
  @Column(name = "cumulative_qty", precision = 18, scale = 3)
  private BigDecimal cumulativeQty;

  @Column(name = "weather_condition", length = 100)
  private String weatherCondition;

  @Column(name = "remarks", length = 1000)
  private String remarks;
}
