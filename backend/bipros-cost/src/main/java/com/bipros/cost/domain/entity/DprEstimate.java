package com.bipros.cost.domain.entity;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "dpr_estimates", schema = "cost")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DprEstimate extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "wbs_node_id", nullable = false)
    private UUID wbsNodeId;

    @Column(name = "cost_category", nullable = false)
    @Enumerated(EnumType.STRING)
    private CostCategory costCategory;

    @Column(name = "estimated_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal estimatedAmount;

    @Column(name = "revised_amount", precision = 19, scale = 2)
    private BigDecimal revisedAmount;

    @Column(name = "remarks")
    private String remarks;

    public enum CostCategory {
        CIVIL, STRUCTURAL, ELECTRICAL, MECHANICAL, LAND, CONTINGENCY, ESCALATION, CONSULTANCY, OTHER
    }
}
