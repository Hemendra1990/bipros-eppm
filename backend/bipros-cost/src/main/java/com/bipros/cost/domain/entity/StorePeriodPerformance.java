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
@Table(name = "store_period_performance", schema = "cost")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StorePeriodPerformance extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "financial_period_id", nullable = false)
    private UUID financialPeriodId;

    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "actual_labor_cost", precision = 19, scale = 2)
    private BigDecimal actualLaborCost;

    @Column(name = "actual_nonlabor_cost", precision = 19, scale = 2)
    private BigDecimal actualNonlaborCost;

    @Column(name = "actual_material_cost", precision = 19, scale = 2)
    private BigDecimal actualMaterialCost;

    @Column(name = "actual_expense_cost", precision = 19, scale = 2)
    private BigDecimal actualExpenseCost;

    @Column(name = "actual_labor_units")
    private Double actualLaborUnits;

    @Column(name = "actual_nonlabor_units")
    private Double actualNonlaborUnits;

    @Column(name = "actual_material_units")
    private Double actualMaterialUnits;

    @Column(name = "earned_value_cost", precision = 19, scale = 2)
    private BigDecimal earnedValueCost;

    @Column(name = "planned_value_cost", precision = 19, scale = 2)
    private BigDecimal plannedValueCost;
}
