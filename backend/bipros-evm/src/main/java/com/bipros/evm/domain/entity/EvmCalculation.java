package com.bipros.evm.domain.entity;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "evm_calculations", schema = "evm")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvmCalculation extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "wbs_node_id")
    private UUID wbsNodeId;

    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "financial_period_id")
    private UUID financialPeriodId;

    @Column(name = "data_date", nullable = false)
    private LocalDate dataDate;

    @Column(name = "budget_at_completion", precision = 19, scale = 2)
    private BigDecimal budgetAtCompletion;

    @Column(name = "planned_value", precision = 19, scale = 2)
    private BigDecimal plannedValue;

    @Column(name = "earned_value", precision = 19, scale = 2)
    private BigDecimal earnedValue;

    @Column(name = "actual_cost", precision = 19, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "schedule_variance", precision = 19, scale = 2)
    private BigDecimal scheduleVariance;

    @Column(name = "cost_variance", precision = 19, scale = 2)
    private BigDecimal costVariance;

    @Column(name = "schedule_performance_index")
    private Double schedulePerformanceIndex;

    @Column(name = "cost_performance_index")
    private Double costPerformanceIndex;

    @Column(name = "to_complete_performance_index")
    private Double toCompletePerformanceIndex;

    @Column(name = "estimate_at_completion", precision = 19, scale = 2)
    private BigDecimal estimateAtCompletion;

    @Column(name = "estimate_to_complete", precision = 19, scale = 2)
    private BigDecimal estimateToComplete;

    @Column(name = "variance_at_completion", precision = 19, scale = 2)
    private BigDecimal varianceAtCompletion;

    @Column(name = "evm_technique")
    @Enumerated(EnumType.STRING)
    private EvmTechnique evmTechnique;

    @Column(name = "etc_method")
    @Enumerated(EnumType.STRING)
    private EtcMethod etcMethod;

    @Column(name = "performance_percent_complete")
    private Double performancePercentComplete;
}
