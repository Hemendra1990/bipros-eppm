package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.CashFlowForecast;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CashFlowForecastDto {
    private UUID id;
    private UUID projectId;
    private String period;
    private BigDecimal plannedAmount;
    private BigDecimal actualAmount;
    private BigDecimal forecastAmount;
    private BigDecimal cumulativePlanned;
    private BigDecimal cumulativeActual;
    private BigDecimal cumulativeForecast;

    public static CashFlowForecastDto from(CashFlowForecast entity) {
        return CashFlowForecastDto.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .period(entity.getPeriod())
                .plannedAmount(entity.getPlannedAmount())
                .actualAmount(entity.getActualAmount())
                .forecastAmount(entity.getForecastAmount())
                .cumulativePlanned(entity.getCumulativePlanned())
                .cumulativeActual(entity.getCumulativeActual())
                .cumulativeForecast(entity.getCumulativeForecast())
                .build();
    }
}
