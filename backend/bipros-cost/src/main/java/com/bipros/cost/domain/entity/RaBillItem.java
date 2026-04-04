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

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "description", nullable = false)
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
