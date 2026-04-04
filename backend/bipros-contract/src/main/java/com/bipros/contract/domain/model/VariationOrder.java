package com.bipros.contract.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "variation_orders", schema = "contract")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VariationOrder extends BaseEntity {

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "vo_number", nullable = false, length = 50)
    private String voNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "vo_value", precision = 15, scale = 2)
    private BigDecimal voValue;

    @Column(columnDefinition = "TEXT")
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VariationOrderStatus status = VariationOrderStatus.INITIATED;

    @Column(name = "impact_on_budget", precision = 15, scale = 2)
    private BigDecimal impactOnBudget;

    @Column(name = "impact_on_schedule_days")
    private Integer impactOnScheduleDays;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
