package com.bipros.cost.domain.entity;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ra_bill_items", schema = "cost")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RaBillItem extends BaseEntity {

    @Column(name = "ra_bill_id", nullable = false)
    private UUID raBillId;

    /**
     * Soft FK to {@code project.boq_items.id} when the line was generated from BOQ; null on
     * legacy hand-typed lines and on lines that don't correspond to a BOQ row (mobilisation,
     * one-off claims, etc.). Lets the RA bill detail page link a line item back to its BOQ
     * source for traceability.
     */
    @Column(name = "boq_item_id")
    private UUID boqItemId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    /**
     * Mirrors the source {@code BoqItem.description} (VARCHAR 500). The default Hibernate
     * column length of 255 is too narrow — civil-works BOQ descriptions routinely run
     * 270–450 characters ("Fabricate and fix IPE 100 ... complete.").
     */
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "unit")
    private String unit;

    @Column(name = "rate", precision = 19, scale = 2)
    private BigDecimal rate;

    @Column(name = "previous_quantity")
    private Double previousQuantity;

    @Column(name = "current_quantity")
    private Double currentQuantity;

    @Column(name = "cumulative_quantity")
    private Double cumulativeQuantity;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;
}
