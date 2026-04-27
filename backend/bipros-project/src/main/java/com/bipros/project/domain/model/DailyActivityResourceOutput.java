package com.bipros.project.domain.model;

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

/**
 * Per-day, per-(activity × resource) output. Bridges the gap between {@link DailyProgressReport}
 * (qty done by activity) and {@link DailyResourceDeployment} (hours deployed by resource): each
 * row pairs the activity and the resource so we can compute *actual productivity* and compare it
 * against the planning-side {@code ProductivityNorm}.
 *
 * <p>All cross-module references are stored as plain UUIDs (no JPA {@code @ManyToOne}) per the
 * no-cross-module-deps rule documented in CLAUDE.md.
 */
@Entity
@Table(
    name = "daily_activity_resource_outputs",
    schema = "project",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_dar_unique",
            columnNames = {"project_id", "output_date", "activity_id", "resource_id"})
    },
    indexes = {
        @Index(name = "idx_dar_project_date", columnList = "project_id, output_date"),
        @Index(name = "idx_dar_activity", columnList = "activity_id"),
        @Index(name = "idx_dar_resource", columnList = "resource_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyActivityResourceOutput extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "output_date", nullable = false)
  private LocalDate outputDate;

  /** Soft FK to {@code activity.activities.id}. Required. */
  @Column(name = "activity_id", nullable = false)
  private UUID activityId;

  /** Soft FK to {@code resource.resources.id}. Required. */
  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "qty_executed", nullable = false, precision = 18, scale = 3)
  private BigDecimal qtyExecuted;

  /** Mirrored from the activity's {@code WorkActivity.defaultUnit} when blank on save. */
  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  /** Total clock-hours the resource spent on this activity on this day. */
  @Column(name = "hours_worked")
  private Double hoursWorked;

  /**
   * Days-equivalent. When {@link #hoursWorked} is set and this is null, the service derives
   * {@code hoursWorked / 8} on save.
   */
  @Column(name = "days_worked")
  private Double daysWorked;

  @Column(name = "remarks", length = 1000)
  private String remarks;
}
