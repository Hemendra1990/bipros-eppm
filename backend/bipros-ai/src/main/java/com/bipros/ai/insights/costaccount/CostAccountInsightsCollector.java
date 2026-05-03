package com.bipros.ai.insights.costaccount;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.ai.insights.dto.ChartSpec;
import com.bipros.evm.application.dto.CostAccountRollupResponse;
import com.bipros.evm.application.service.EvmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CostAccountInsightsCollector implements InsightDataCollector {

    private final EvmService evmService;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        List<CostAccountRollupResponse> rows = evmService.getCostAccountRollup(projectId);

        BigDecimal totalBac = rows.stream()
                .map(r -> r.bac() != null ? r.bac() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEv = rows.stream()
                .map(r -> r.ev() != null ? r.ev() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAc = rows.stream()
                .map(r -> r.ac() != null ? r.ac() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weightedCpi = totalAc.compareTo(BigDecimal.ZERO) > 0
                ? totalEv.divide(totalAc, 4, RoundingMode.HALF_UP)
                : null;

        int unassignedCount = rows.stream()
                .filter(r -> r.costAccountId() == null)
                .findFirst()
                .map(CostAccountRollupResponse::activityCount)
                .orElse(0);

        List<Map<String, Object>> topAccounts = rows.stream()
                .filter(r -> r.costAccountId() != null)
                .limit(10)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", r.costAccountName());
                    m.put("bac", r.bac());
                    m.put("ev", r.ev());
                    m.put("ac", r.ac());
                    m.put("cpi", r.cpi());
                    m.put("activityCount", r.activityCount());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("totalBac", totalBac);
        snapshot.put("totalEv", totalEv);
        snapshot.put("totalAc", totalAc);
        snapshot.put("weightedCpi", weightedCpi);
        snapshot.put("unassignedActivityCount", unassignedCount);
        snapshot.put("topAccounts", topAccounts);

        return objectMapper.valueToTree(snapshot);
    }

    @Override
    public List<ChartSpec> charts(UUID projectId) {
        if (projectId == null) {
            return List.of(
                    new ChartSpec("ca-treemap", "Cost by Account", "treemap", null, null),
                    new ChartSpec("ca-variance", "Account Variance", "bar", null, null)
            );
        }

        List<CostAccountRollupResponse> rows = evmService.getCostAccountRollup(projectId);
        List<CostAccountRollupResponse> accounts = rows.stream()
                .filter(r -> r.costAccountId() != null)
                .limit(10)
                .toList();
        List<ChartSpec> charts = new ArrayList<>();

        // Treemap of cost by account
        ObjectNode treemapOption = objectMapper.createObjectNode();
        ObjectNode treemapSeries = objectMapper.createObjectNode();
        treemapSeries.put("type", "treemap");
        ArrayNode treemapData = objectMapper.createArrayNode();
        accounts.forEach(r -> {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("name", r.costAccountName() != null ? r.costAccountName() : "Unknown");
            item.put("value", r.bac() != null ? r.bac().doubleValue() : 0);
            treemapData.add(item);
        });
        treemapSeries.set("data", treemapData);
        treemapOption.set("series", treemapSeries);
        charts.add(new ChartSpec("ca-treemap", "Cost by Account", "treemap", treemapOption,
                "Budget at Completion by cost account"));

        // Variance bar (BAC - EV for each account)
        ObjectNode varOption = objectMapper.createObjectNode();
        ObjectNode varTooltip = objectMapper.createObjectNode();
        varTooltip.put("trigger", "axis");
        varOption.set("tooltip", varTooltip);
        ObjectNode varXAxis = objectMapper.createObjectNode();
        varXAxis.put("type", "category");
        ArrayNode varCats = objectMapper.createArrayNode();
        accounts.forEach(r -> varCats.add(r.costAccountName() != null ? r.costAccountName() : ""));
        varXAxis.set("data", varCats);
        varOption.set("xAxis", varXAxis);
        ObjectNode varYAxis = objectMapper.createObjectNode();
        varYAxis.put("type", "value");
        varOption.set("yAxis", varYAxis);
        ArrayNode varSeries = objectMapper.createArrayNode();
        ObjectNode varBar = objectMapper.createObjectNode();
        varBar.put("name", "Variance (BAC-EV)");
        varBar.put("type", "bar");
        ArrayNode varData = objectMapper.createArrayNode();
        accounts.forEach(r -> {
            BigDecimal bac = r.bac() != null ? r.bac() : BigDecimal.ZERO;
            BigDecimal ev = r.ev() != null ? r.ev() : BigDecimal.ZERO;
            varData.add(bac.subtract(ev));
        });
        varBar.set("data", varData);
        varSeries.add(varBar);
        varOption.set("series", varSeries);
        charts.add(new ChartSpec("ca-variance", "Account Variance", "bar", varOption,
                "BAC minus EV by cost account"));

        return charts;
    }

    @Override
    public String tabKey() {
        return "cost-accounts";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to improve cost account performance. Focus on unassigned activities, cost accounts with negative CPI, and EV/AC variance by account.";
    }
}
