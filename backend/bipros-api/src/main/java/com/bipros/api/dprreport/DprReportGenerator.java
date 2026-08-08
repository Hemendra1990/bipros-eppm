package com.bipros.api.dprreport;

import com.bipros.ai.insights.InsightsGenerator;
import com.bipros.ai.insights.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class DprReportGenerator {
    private static final String TAB = "dpr-report";
    private static final String INSTRUCTIONS = """
        You are analyzing a construction project's Daily Progress Reports and Capacity Utilization for a time window.
        The data snapshot already contains COMPUTED metrics and anomalies — treat them as ground truth.
        Write: (1) a concise executive summary; (2) findings that INTERPRET each anomaly (why it matters, likely cause
        from issues/voice notes); (3) prioritized, actionable recommendations. Reference ONLY numbers present in the
        snapshot; never invent figures. Positive cost implication = overrun, negative = saving. Money is in the given
        currency code; do not convert.
        """;

    private final InsightsGenerator insightsGenerator;
    private final ObjectMapper objectMapper;
    private final DprReportHighlightBuilder highlightBuilder;
    private final DprReportChartBuilder chartBuilder;

    public InsightsResponse generate(DprReportSnapshot snapshot, DprReportMetrics metrics) {
        JsonNode snap = buildSnapshotJson(snapshot, metrics);
        List<ChartSpec> charts = chartBuilder.charts(metrics);
        InsightsResponse llm;
        try {
            llm = insightsGenerator.generate(TAB, snap, INSTRUCTIONS, charts);
        } catch (Exception llmDown) {
            // Robustness (2026-08-05): the daily report must not die with the LLM — every number is
            // deterministic; only the narrative is AI. Ship the report without it and say so.
            log.warn("[DprReportGenerator] LLM narrative unavailable, sending deterministic-only report: {}",
                llmDown.getMessage());
            llm = new InsightsResponse(
                "Automated daily record (AI narrative unavailable: " + llmDown.getMessage() + ")",
                List.of(), List.of(), List.of(), List.of(), null, null, charts);
        }
        // Override the numeric surfaces with deterministic values (never trust LLM numbers there):
        return new InsightsResponse(
            llm.summary(),
            highlightBuilder.highlights(metrics),
            highlightBuilder.variances(metrics),
            llm.recommendations(),
            llm.findings(),
            llm.rationale(),
            llm.mdx(),
            charts);
    }

    private JsonNode buildSnapshotJson(DprReportSnapshot s, DprReportMetrics m) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("projectName", m.projectName);
        root.put("currency", m.currencyCode);
        root.put("windowLabel", s.request().windowLabel());
        root.put("totalApprovedDprs", m.totalDprs);
        root.put("totalActualCost", m.totalActualCost);
        root.put("totalBudgetedCost", m.totalBudgetedCost);
        root.put("totalCostVariance", m.totalCostVariance);
        root.put("openIssues", m.openIssues);
        root.put("criticalIssues", m.criticalIssues);
        root.put("safetyIncidents", m.safetyIncidents);
        root.set("roleEfficiencies", objectMapper.valueToTree(m.roleEfficiencies));
        root.set("anomalies", objectMapper.valueToTree(m.anomalies));
        root.set("voiceTranscripts", objectMapper.valueToTree(
            s.voiceTranscripts() == null ? List.of() : s.voiceTranscripts().stream().limit(30).toList()));
        return root;
    }
}
