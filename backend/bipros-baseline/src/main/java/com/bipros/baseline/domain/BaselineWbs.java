package com.bipros.baseline.domain;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 5.1: snapshot of a {@code WbsNode} captured at the moment a baseline is taken.
 * Lets variance reports answer "what did the WBS look like in this baseline?" even after the
 * live WBS has been reorganised (renames, re-parented nodes, deletions).
 */
@Entity
@Table(
    name = "baseline_wbs",
    schema = "baseline",
    uniqueConstraints = @UniqueConstraint(columnNames = {"baseline_id", "wbs_node_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaselineWbs extends BaseEntity {

  @Column(name = "baseline_id", nullable = false)
  private UUID baselineId;

  /** The live WBS node ID this row snapshots. May be null if the node was created post-snapshot. */
  @Column(name = "wbs_node_id", nullable = false)
  private UUID wbsNodeId;

  @Column(nullable = false, length = 20)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  /** Snapshotted parent — may point at a wbs_node_id that has since been deleted. */
  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "wbs_level")
  private Integer wbsLevel;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "planned_start")
  private LocalDate plannedStart;

  @Column(name = "planned_finish")
  private LocalDate plannedFinish;

  /** Budget snapshot — uses the live {@code budget_crores} value at snapshot time. */
  @Column(name = "budget_crores", precision = 14, scale = 2)
  private BigDecimal budgetCrores;
}
