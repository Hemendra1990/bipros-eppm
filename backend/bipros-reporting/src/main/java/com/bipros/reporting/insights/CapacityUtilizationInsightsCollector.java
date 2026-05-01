package com.bipros.reporting.insights;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.reporting.application.dto.CapacityUtilizationReport;
import com.bipros.reporting.application.service.CapacityUtilizationReportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CapacityUtilizationInsightsCollector implements InsightDataCollector {

    private final CapacityUtilizationReportService reportService;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        CapacityUtilizationReport report = reportService.build(projectId, null, null, "RESOURCE_TYPE", null);
        List<CapacityUtilizationReport.Row> rows = report.rows();

        ObjectNode root = objectMapper.createObjectNode();
        root.put("projectId", projectId.toString());
        root.put("totalRows", rows.size());
        root.put("groupBy", report.groupBy());

        ArrayNode topRows = root.putArray("topRows");
        rows.stream()
                .sorted(Comparator.comparing((CapacityUtilizationReport.Row r) -> {
                    return r.cumulative().qty() != null ? r.cumulative().qty() : BigDecimal.ZERO;
                }).reversed())
                .limit(10)
                .forEach(r -> {
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("workActivityCode", r.workActivity().code());
                    node.put("workActivityName", r.workActivity().name());
                    node.put("groupLabel", r.groupKey().displayLabel());
                    node.put("budgetedOutputPerDay", r.budgeted().outputPerDay() != null ? r.budgeted().outputPerDay().doubleValue() : null);
                    node.put("cumulativeQty", r.cumulative().qty() != null ? r.cumulative().qty().doubleValue() : null);
                    node.put("cumulativeDays", r.cumulative().actualDays() != null ? r.cumulative().actualDays().doubleValue() : null);
                    node.put("cumulativeUtilizationPct", r.cumulative().utilizationPct() != null ? r.cumulative().utilizationPct().doubleValue() : null);
                    node.put("dayUtilizationPct", r.forTheDay().utilizationPct() != null ? r.forTheDay().utilizationPct().doubleValue() : null);
                    node.put("monthUtilizationPct", r.forTheMonth().utilizationPct() != null ? r.forTheMonth().utilizationPct().doubleValue() : null);
                    topRows.add(node);
                });

        long underUtilizedCount = rows.stream()
                .filter(r -> r.cumulative().utilizationPct() != null && r.cumulative().utilizationPct().compareTo(BigDecimal.valueOf(80)) < 0)
                .count();
        root.put("underUtilizedRowCount", underUtilizedCount);

        return root;
    }

    @Override
    public String tabKey() {
        return "capacity-utilization";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to improve equipment and labour utilization. Focus on under-performing work activities, resources exceeding norms, utilization trends, and productivity bottlenecks.";
    }
}
