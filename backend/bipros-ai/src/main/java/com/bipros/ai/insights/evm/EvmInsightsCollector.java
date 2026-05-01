package com.bipros.ai.insights.evm;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.evm.application.dto.EvmCalculationResponse;
import com.bipros.evm.application.dto.EvmSummaryResponse;
import com.bipros.evm.application.service.EvmService;
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
    public String tabKey() {
        return "evm";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to recover schedule and control cost. Focus on SPI/CPI trends, EAC vs BAC variance, and schedule recovery recommendations.";
    }
}
