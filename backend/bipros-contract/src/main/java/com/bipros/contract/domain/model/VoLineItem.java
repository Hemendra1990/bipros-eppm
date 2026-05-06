package com.bipros.contract.domain.model;

import com.bipros.common.event.VoLineItemAction;
import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Structured BOQ mutation associated with a {@link VariationOrder}. Each row instructs the
 * BOQ side (in {@code bipros-project}) to add / revise / delete a {@code BoqItem} when the
 * parent VO is approved. The {@code variation_order_id} FK is a soft FK (no @ManyToOne) to
 * keep cross-aggregate transitions explicit and avoid cascade-load surprises.
 */
@Entity
@Table(
    name = "vo_line_items",
    schema = "contract",
    indexes = {
        @Index(name = "idx_vo_line_item_vo_id", columnList = "variation_order_id"),
        @Index(name = "idx_vo_line_item_boq_id", columnList = "boq_item_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoLineItem extends BaseEntity {

  @Column(name = "variation_order_id", nullable = false)
  private UUID variationOrderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 20)
  private VoLineItemAction action;

  /** Existing BoqItem to mutate. Null when {@link #action} = ADD_ITEM and the row is brand new. */
  @Column(name = "boq_item_id")
  private UUID boqItemId;

  @Column(name = "new_item_no", length = 20)
  private String newItemNo;

  @Column(name = "new_item_description", length = 500)
  private String newItemDescription;

  @Column(name = "new_item_unit", length = 20)
  private String newItemUnit;

  @Column(name = "revised_qty", precision = 18, scale = 3)
  private BigDecimal revisedQty;

  @Column(name = "revised_rate", precision = 18, scale = 4)
  private BigDecimal revisedRate;

  /** Computed: this line's contribution to the parent VO's value. Stored, not derived, so historical reads stay consistent if rates change later. */
  @Column(name = "line_impact_amount", precision = 19, scale = 2)
  private BigDecimal lineImpactAmount;
}
