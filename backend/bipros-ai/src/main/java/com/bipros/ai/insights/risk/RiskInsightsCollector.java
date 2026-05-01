package com.bipros.ai.insights.risk;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.risk.application.dto.RiskSummary;
import com.bipros.risk.application.service.RiskService;
import com.bipros.risk.domain.model.RiskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RiskInsightsCollector implements InsightDataCollector {

    private final RiskService riskService;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        List<RiskSummary> allRisks = riskService.listRisks(projectId, null);
        BigDecimal totalExposure = riskService.calculateRiskExposure(projectId);

        long totalCount = allRisks.size();
        long openCount = allRisks.stream()
                .filter(r -> r.getStatus() != null && !isTerminal(r.getStatus()))
                .count();
        long closedCount = allRisks.stream()
                .filter(r -> r.getStatus() != null && isTerminal(r.getStatus()))
                .count();

        List<RiskSummary> highScoreOpenRisks = allRisks.stream()
                .filter(r -> r.getStatus() != null && !isTerminal(r.getStatus()))
                .filter(r -> r.getRiskScore() != null)
                .sorted((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()))
                .limit(5)
                .toList();

        Map<String, Long> statusBreakdown = allRisks.stream()
                .filter(r -> r.getStatus() != null)
                .collect(Collectors.groupingBy(r -> r.getStatus().name(), Collectors.counting()));

        Map<String, Long> ragBreakdown = allRisks.stream()
                .filter(r -> r.getRag() != null)
                .collect(Collectors.groupingBy(r -> r.getRag().name(), Collectors.counting()));

        ObjectNode root = objectMapper.createObjectNode();
        root.put("totalRiskCount", totalCount);
        root.put("openCount", openCount);
        root.put("closedCount", closedCount);
        root.put("totalExposureCost", totalExposure != null ? totalExposure.doubleValue() : 0.0);

        ArrayNode topRisksArray = root.putArray("highScoreOpenRisks");
        for (RiskSummary r : highScoreOpenRisks) {
            ObjectNode riskNode = topRisksArray.addObject();
            riskNode.put("code", r.getCode());
            riskNode.put("title", r.getTitle());
            riskNode.put("probability", r.getProbability() != null ? r.getProbability().name() : null);
            riskNode.put("impact", r.getImpact() != null ? r.getImpact().name() : null);
            riskNode.put("score", r.getRiskScore());
            riskNode.put("status", r.getStatus() != null ? r.getStatus().name() : null);
            riskNode.put("dueDate", r.getDueDate() != null ? r.getDueDate().toString() : null);
        }

        ObjectNode statusNode = root.putObject("risksByStatus");
        statusBreakdown.forEach(statusNode::put);

        ObjectNode ragNode = root.putObject("risksByRag");
        ragBreakdown.forEach(ragNode::put);

        return root;
    }

    @Override
    public String tabKey() {
        return "risks";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to mitigate top risks and improve risk management. "
                + "Focus on open high-score risks, overdue risks, RAG status breakdown, exposure trends, and response gaps.";
    }

    private static boolean isTerminal(RiskStatus status) {
        return status == RiskStatus.CLOSED || status == RiskStatus.RESOLVED;
    }
}
