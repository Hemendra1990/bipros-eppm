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
import java.util.UUID;

/**
 * One operation of a split BOQ line (split design 2026-08-03 §4.2, D1). Operations carry NO money —
 * rates stay on the {@link BoqItem} (D2); an operation only contributes its weighted completion to
 * the line's {@code earnedFraction} and, when {@link #isMeasure}, its executed quantity becomes the
 * line's measured {@code qtyExecutedToDate} (billing basis, D3).
 *
 * <p>{@code targetQty} null means a milestone operation: any executed quantity counts as fully done.
 * {@code isLegacy} marks the auto-created operation that absorbs pre-split DPR history (§7.3) —
 * DPRs with a null {@code boq_operation_id} on a split line resolve to it at recompute time.
 */
@Entity
@Table(
    name = "boq_operations",
    schema = "project",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_boq_op_item_code", columnNames = {"boq_item_id", "op_code"})
    },
    indexes = {
        @Index(name = "idx_boq_op_item", columnList = "boq_item_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoqOperation extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "boq_item_id", nullable = false)
  private UUID boqItemId;

  @Column(name = "op_code", nullable = false, length = 40)
  private String opCode;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  /** Optional master work-activity reference (norms/AI context only — no money). */
  @Column(name = "work_activity_id")
  private UUID workActivityId;

  /** Operation's own unit — may differ from the line's unit except on the measurement operation. */
  @Column(name = "unit", length = 20)
  private String unit;

  /** Operation target quantity in its own unit. Null = milestone (binary complete). */
  @Column(name = "target_qty", precision = 18, scale = 3)
  private BigDecimal targetQty;

  /** Weight toward the line's earned fraction (WEIGHTED_OPERATIONS mode; Σ per line ≈ 100). */
  @Builder.Default
  @Column(name = "weight_pct", precision = 6, scale = 3)
  private BigDecimal weightPct = BigDecimal.ZERO;

  /** The one operation whose executed qty is the line's measured/billable quantity (D3). */
  @Builder.Default
  @Column(name = "is_measure")
  private Boolean isMeasure = Boolean.FALSE;

  /** Auto-created absorber of pre-split DPR history (§7.3). */
  @Builder.Default
  @Column(name = "is_legacy")
  private Boolean isLegacy = Boolean.FALSE;

  /** Display/sequence order (Q6 out-of-sequence warning uses it). */
  @Builder.Default
  @Column(name = "sort_order")
  private Integer sortOrder = 0;
}
