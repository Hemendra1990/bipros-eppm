package com.bipros.ai.insights.cost;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.dto.PeriodCostAggregationDto;
import com.bipros.cost.application.service.CostService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CostInsightsCollector implements InsightDataCollector {

    private final CostService costService;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        CostSummaryDto summary = costService.getCostSummary(projectId);
        List<PeriodCostAggregationDto> periods = costService.aggregateByPeriod(projectId);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("totalBudget", summary.totalBudget());
        snapshot.put("totalActual", summary.totalActual());
        snapshot.put("totalRemaining", summary.totalRemaining());
        snapshot.put("atCompletion", summary.atCompletion());
        snapshot.put("expenseCount", summary.expenseCount());
        snapshot.put("costVariance", summary.costVariance());
        snapshot.put("costPerformanceIndex", summary.costPerformanceIndex());
        snapshot.put("materialProcurementCost", summary.materialProcurementCost());

        List<Map<String, Object>> topPeriods = periods.stream()
                .limit(5)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("periodName", p.periodName());
                    m.put("budget", p.budget());
                    m.put("actual", p.actual());
                    m.put("variance", p.variance());
                    return m;
                })
                .collect(Collectors.toList());
        snapshot.put("topPeriods", topPeriods);

        return objectMapper.valueToTree(snapshot);
    }

    @Override
    public String tabKey() {
        return "costs";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to reduce cost and improve budget control. Focus on budget vs actual variance, CPI trends, and period-over-period cost anomalies.";
    }
}
