package com.bipros.ai.insights.cost;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.ai.insights.dto.ChartSpec;
import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.dto.PeriodCostAggregationDto;
import com.bipros.cost.application.service.CostService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    public List<ChartSpec> charts(UUID projectId) {
        if (projectId == null) {
            return List.of(
                    new ChartSpec("cost-bvp", "Budget vs Actual by Period", "bar", null, null),
                    new ChartSpec("cost-cpi", "Cost Performance Index", "gauge", null, null),
                    new ChartSpec("cost-variance", "Cost Variance Breakdown", "waterfall", null, null)
            );
        }

        CostSummaryDto summary = costService.getCostSummary(projectId);
        List<PeriodCostAggregationDto> periods = costService.aggregateByPeriod(projectId);
        List<ChartSpec> charts = new ArrayList<>();

        // Budget vs Actual bar chart by top-5 periods
        List<PeriodCostAggregationDto> top5 = periods.stream().limit(5).toList();
        ObjectNode bvpOption = objectMapper.createObjectNode();
        ObjectNode bvpTooltip = objectMapper.createObjectNode();
        bvpTooltip.put("trigger", "axis");
        bvpOption.set("tooltip", bvpTooltip);
        bvpOption.set("legend", objectMapper.createObjectNode());
        ObjectNode bvpXAxis = objectMapper.createObjectNode();
        bvpXAxis.put("type", "category");
        ArrayNode bvpCategories = objectMapper.createArrayNode();
        top5.forEach(p -> bvpCategories.add(p.periodName()));
        bvpXAxis.set("data", bvpCategories);
        bvpOption.set("xAxis", bvpXAxis);
        ObjectNode bvpYAxis = objectMapper.createObjectNode();
        bvpYAxis.put("type", "value");
        bvpOption.set("yAxis", bvpYAxis);
        ArrayNode bvpSeries = objectMapper.createArrayNode();
        ObjectNode budgetSeries = objectMapper.createObjectNode();
        budgetSeries.put("name", "Budget");
        budgetSeries.put("type", "bar");
        ArrayNode budgetData = objectMapper.createArrayNode();
        top5.forEach(p -> budgetData.add(p.budget()));
        budgetSeries.set("data", budgetData);
        bvpSeries.add(budgetSeries);
        ObjectNode actualSeries = objectMapper.createObjectNode();
        actualSeries.put("name", "Actual");
        actualSeries.put("type", "bar");
        ArrayNode actualData = objectMapper.createArrayNode();
        top5.forEach(p -> actualData.add(p.actual()));
        actualSeries.set("data", actualData);
        bvpSeries.add(actualSeries);
        bvpOption.set("series", bvpSeries);
        charts.add(new ChartSpec("cost-bvp", "Budget vs Actual by Period", "bar", bvpOption,
                "Top 5 periods by budget"));

        // CPI gauge
        ObjectNode cpiOption = objectMapper.createObjectNode();
        cpiOption.put("series", "gauge");
        ObjectNode cpiSeries = objectMapper.createObjectNode();
        cpiSeries.put("type", "gauge");
        cpiSeries.put("min", 0);
        cpiSeries.put("max", 2);
        ObjectNode cpiDetail = objectMapper.createObjectNode();
        cpiDetail.put("formatter", "{value}");
        cpiDetail.put("fontSize", 20);
        cpiSeries.set("detail", cpiDetail);
        ArrayNode cpiData = objectMapper.createArrayNode();
        ObjectNode cpiDataItem = objectMapper.createObjectNode();
        cpiDataItem.put("value", summary.costPerformanceIndex() != null ? summary.costPerformanceIndex().doubleValue() : 0.0);
        cpiDataItem.put("name", "CPI");
        cpiData.add(cpiDataItem);
        cpiSeries.set("data", cpiData);
        cpiOption.set("series", cpiSeries);
        charts.add(new ChartSpec("cost-cpi", "Cost Performance Index", "gauge", cpiOption,
                "CPI > 1.0 is under budget"));

        // Variance waterfall
        ObjectNode varOption = objectMapper.createObjectNode();
        ObjectNode varXAxis = objectMapper.createObjectNode();
        varXAxis.put("type", "category");
        ArrayNode varCategories = objectMapper.createArrayNode();
        varCategories.add("Budget");
        varCategories.add("Actual");
        varCategories.add("Variance");
        varXAxis.set("data", varCategories);
        varOption.set("xAxis", varXAxis);
        ObjectNode varYAxis = objectMapper.createObjectNode();
        varYAxis.put("type", "value");
        varOption.set("yAxis", varYAxis);
        ArrayNode varSeries = objectMapper.createArrayNode();
        ObjectNode waterfall = objectMapper.createObjectNode();
        waterfall.put("type", "bar");
        waterfall.put("stack", "total");
        ArrayNode varData = objectMapper.createArrayNode();
        varData.add(summary.totalBudget() != null ? summary.totalBudget().doubleValue() : 0.0);
        varData.add(0.0);
        varData.add(0.0);
        waterfall.set("data", varData);
        varSeries.add(waterfall);
        ObjectNode actualBar = objectMapper.createObjectNode();
        actualBar.put("type", "bar");
        actualBar.put("stack", "total");
        ArrayNode actualBarData = objectMapper.createArrayNode();
        actualBarData.add(0.0);
        actualBarData.add(summary.totalActual() != null ? summary.totalActual().doubleValue() : 0.0);
        actualBarData.add(0.0);
        actualBar.set("data", actualBarData);
        varSeries.add(actualBar);
        ObjectNode varianceBar = objectMapper.createObjectNode();
        varianceBar.put("type", "bar");
        varianceBar.put("stack", "total");
        ArrayNode varianceBarData = objectMapper.createArrayNode();
        varianceBarData.add(0.0);
        varianceBarData.add(0.0);
        varianceBarData.add(summary.costVariance() != null ? summary.costVariance().doubleValue() : 0.0);
        varianceBar.set("data", varianceBarData);
        varSeries.add(varianceBar);
        varOption.set("series", varSeries);
        charts.add(new ChartSpec("cost-variance", "Cost Variance Breakdown", "waterfall", varOption,
                "Budget vs Actual vs Variance"));

        return charts;
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
