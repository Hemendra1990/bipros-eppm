package com.bipros.ai.insights.evm;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.ai.insights.dto.ChartSpec;
import com.bipros.evm.application.dto.EvmCalculationResponse;
import com.bipros.evm.application.dto.EvmSummaryResponse;
import com.bipros.evm.application.service.EvmService;
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
public class EvmInsightsCollector implements InsightDataCollector {

    private final EvmService evmService;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        EvmSummaryResponse summary = evmService.getSummary(projectId);
        List<EvmCalculationResponse> history = evmService.getEvmHistory(projectId);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pv", summary.plannedValue());
        snapshot.put("ev", summary.earnedValue());
        snapshot.put("ac", summary.actualCost());
        snapshot.put("sv", summary.scheduleVariance());
        snapshot.put("cv", summary.costVariance());
        snapshot.put("spi", summary.schedulePerformanceIndex());
        snapshot.put("cpi", summary.costPerformanceIndex());
        snapshot.put("eac", summary.estimateAtCompletion());
        snapshot.put("etc", summary.estimateToComplete());
        snapshot.put("vac", summary.varianceAtCompletion());
        snapshot.put("tcpi", summary.toCompletePerformanceIndex());
        snapshot.put("bac", summary.budgetAtCompletion());
        snapshot.put("performancePercentComplete", summary.performancePercentComplete());

        List<Map<String, Object>> historyTrend = history.stream()
                .limit(5)
                .map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("dataDate", h.dataDate() != null ? h.dataDate().toString() : null);
                    m.put("pv", h.plannedValue());
                    m.put("ev", h.earnedValue());
                    m.put("ac", h.actualCost());
                    m.put("spi", h.schedulePerformanceIndex());
                    m.put("cpi", h.costPerformanceIndex());
                    return m;
                })
                .collect(Collectors.toList());
        snapshot.put("historyTrend", historyTrend);

        return objectMapper.valueToTree(snapshot);
    }

    @Override
    public List<ChartSpec> charts(UUID projectId) {
        if (projectId == null) {
            return List.of(
                    new ChartSpec("evm-trend", "PV/EV/AC Trend", "line", null, null),
                    new ChartSpec("evm-spi-cpi", "SPI & CPI Gauges", "dual-gauge", null, null),
                    new ChartSpec("evm-eac-bac", "EAC vs BAC", "stacked-bar", null, null)
            );
        }

        EvmSummaryResponse summary = evmService.getSummary(projectId);
        List<EvmCalculationResponse> history = evmService.getEvmHistory(projectId);
        List<ChartSpec> charts = new ArrayList<>();

        // PV/EV/AC line trend
        List<EvmCalculationResponse> recentHistory = history.stream().limit(5).toList();
        ObjectNode trendOption = objectMapper.createObjectNode();
        ObjectNode trendTooltip = objectMapper.createObjectNode();
        trendTooltip.put("trigger", "axis");
        trendOption.set("tooltip", trendTooltip);
        trendOption.set("legend", objectMapper.createObjectNode());
        ObjectNode trendXAxis = objectMapper.createObjectNode();
        trendXAxis.put("type", "category");
        ArrayNode trendCategories = objectMapper.createArrayNode();
        recentHistory.forEach(h -> trendCategories.add(h.dataDate() != null ? h.dataDate().toString() : ""));
        trendXAxis.set("data", trendCategories);
        trendOption.set("xAxis", trendXAxis);
        ObjectNode trendYAxis = objectMapper.createObjectNode();
        trendYAxis.put("type", "value");
        trendOption.set("yAxis", trendYAxis);
        ArrayNode trendSeries = objectMapper.createArrayNode();
        for (String field : List.of("plannedValue", "earnedValue", "actualCost")) {
            String label = field.equals("plannedValue") ? "PV" : field.equals("earnedValue") ? "EV" : "AC";
            ObjectNode s = objectMapper.createObjectNode();
            s.put("name", label);
            s.put("type", "line");
            ArrayNode data = objectMapper.createArrayNode();
            recentHistory.forEach(h -> {
                switch (field) {
                    case "plannedValue" -> data.add(h.plannedValue() != null ? h.plannedValue().doubleValue() : 0.0);
                    case "earnedValue" -> data.add(h.earnedValue() != null ? h.earnedValue().doubleValue() : 0.0);
                    case "actualCost" -> data.add(h.actualCost() != null ? h.actualCost().doubleValue() : 0.0);
                }
            });
            s.set("data", data);
            trendSeries.add(s);
        }
        trendOption.set("series", trendSeries);
        charts.add(new ChartSpec("evm-trend", "PV/EV/AC Trend", "line", trendOption,
                "Planned Value, Earned Value, and Actual Cost over time"));

        // SPI/CPI dual gauge
        ObjectNode gaugeOption = objectMapper.createObjectNode();
        ArrayNode gaugeSeries = objectMapper.createArrayNode();
        ObjectNode spiGauge = objectMapper.createObjectNode();
        spiGauge.put("type", "gauge");
        spiGauge.put("min", 0);
        spiGauge.put("max", 2);
        ObjectNode spiCenter = objectMapper.createObjectNode();
        spiCenter.put("x", "25%");
        spiCenter.put("y", "55%");
        spiGauge.set("center", spiCenter);
        ObjectNode spiTitle = objectMapper.createObjectNode();
        ArrayNode spiOffset = objectMapper.createArrayNode();
        spiOffset.add(0);
        spiOffset.add("80%");
        spiTitle.set("offsetCenter", spiOffset);
        spiGauge.set("title", spiTitle);
        ArrayNode spiData = objectMapper.createArrayNode();
        ObjectNode spiItem = objectMapper.createObjectNode();
        spiItem.put("value", summary.schedulePerformanceIndex() != null ? summary.schedulePerformanceIndex().doubleValue() : 0.0);
        spiItem.put("name", "SPI");
        spiData.add(spiItem);
        spiGauge.set("data", spiData);
        gaugeSeries.add(spiGauge);
        ObjectNode cpiGauge = objectMapper.createObjectNode();
        cpiGauge.put("type", "gauge");
        cpiGauge.put("min", 0);
        cpiGauge.put("max", 2);
        ObjectNode cpiCenter = objectMapper.createObjectNode();
        cpiCenter.put("x", "75%");
        cpiCenter.put("y", "55%");
        cpiGauge.set("center", cpiCenter);
        ObjectNode cpiTitle = objectMapper.createObjectNode();
        ArrayNode cpiOffset = objectMapper.createArrayNode();
        cpiOffset.add(0);
        cpiOffset.add("80%");
        cpiTitle.set("offsetCenter", cpiOffset);
        cpiGauge.set("title", cpiTitle);
        ArrayNode cpiData = objectMapper.createArrayNode();
        ObjectNode cpiItem = objectMapper.createObjectNode();
        cpiItem.put("value", summary.costPerformanceIndex() != null ? summary.costPerformanceIndex().doubleValue() : 0.0);
        cpiItem.put("name", "CPI");
        cpiData.add(cpiItem);
        cpiGauge.set("data", cpiData);
        gaugeSeries.add(cpiGauge);
        gaugeOption.set("series", gaugeSeries);
        charts.add(new ChartSpec("evm-spi-cpi", "SPI & CPI Gauges", "dual-gauge", gaugeOption,
                "Schedule and Cost Performance Indices"));

        // EAC vs BAC stacked bar
        ObjectNode eacOption = objectMapper.createObjectNode();
        ObjectNode eacXAxis = objectMapper.createObjectNode();
        eacXAxis.put("type", "category");
        ArrayNode eacCats = objectMapper.createArrayNode();
        eacCats.add("BAC");
        eacCats.add("EAC");
        eacXAxis.set("data", eacCats);
        eacOption.set("xAxis", eacXAxis);
        ObjectNode eacYAxis = objectMapper.createObjectNode();
        eacYAxis.put("type", "value");
        eacOption.set("yAxis", eacYAxis);
        ArrayNode eacSeries = objectMapper.createArrayNode();
        ObjectNode eacBar = objectMapper.createObjectNode();
        eacBar.put("type", "bar");
        eacBar.put("name", "Amount");
        ArrayNode eacData = objectMapper.createArrayNode();
        eacData.add(summary.budgetAtCompletion() != null ? summary.budgetAtCompletion().doubleValue() : 0.0);
        eacData.add(summary.estimateAtCompletion() != null ? summary.estimateAtCompletion().doubleValue() : 0.0);
        eacBar.set("data", eacData);
        eacSeries.add(eacBar);
        eacOption.set("series", eacSeries);
        charts.add(new ChartSpec("evm-eac-bac", "EAC vs BAC", "stacked-bar", eacOption,
                "Estimate at Completion vs Budget at Completion"));

        return charts;
    }

    @Override
    public String tabKey() {
        return "evm";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to recover schedule and control cost. Focus on SPI/CPI trends, EAC vs BAC variance, and schedule recovery recommendations.";
    }
}
