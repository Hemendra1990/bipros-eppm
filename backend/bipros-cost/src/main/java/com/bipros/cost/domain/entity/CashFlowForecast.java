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
@Table(name = "cash_flow_forecasts", schema = "cost")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowForecast extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "period", nullable = false)
    private String period;

    @Column(name = "planned_amount", precision = 19, scale = 2)
    private BigDecimal plannedAmount;

    @Column(name = "actual_amount", precision = 19, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "forecast_amount", precision = 19, scale = 2)
    private BigDecimal forecastAmount;

    @Column(name = "cumulative_planned", precision = 19, scale = 2)
    private BigDecimal cumulativePlanned;

    @Column(name = "cumulative_actual", precision = 19, scale = 2)
    private BigDecimal cumulativeActual;

    @Column(name = "cumulative_forecast", precision = 19, scale = 2)
    private BigDecimal cumulativeForecast;
}
