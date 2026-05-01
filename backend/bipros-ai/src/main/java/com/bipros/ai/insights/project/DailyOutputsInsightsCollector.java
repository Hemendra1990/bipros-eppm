package com.bipros.ai.insights.project;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.project.application.dto.DailyActivityResourceOutputResponse;
import com.bipros.project.application.service.DailyActivityResourceOutputService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DailyOutputsInsightsCollector implements InsightDataCollector {

    private final DailyActivityResourceOutputService outputService;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        List<DailyActivityResourceOutputResponse> rows = outputService.list(projectId, null, null, null, null);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("projectId", projectId.toString());
        root.put("totalRecords", rows.size());

        Map<String, List<DailyActivityResourceOutputResponse>> byPair = rows.stream()
                .collect(Collectors.groupingBy(r -> r.activityId() + "|" + r.resourceId()));

        ArrayNode pairs = root.putArray("topPairs");
        byPair.entrySet().stream()
                .map(e -> {
                    List<DailyActivityResourceOutputResponse> list = e.getValue();
                    BigDecimal totalQty = list.stream()
                            .map(DailyActivityResourceOutputResponse::qtyExecuted)
                            .filter(q -> q != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    double totalHours = list.stream()
                            .mapToDouble(r -> r.hoursWorked() != null ? r.hoursWorked() : 0.0)
                            .sum();
                    double totalDays = list.stream()
                            .mapToDouble(r -> {
                                if (r.daysWorked() != null) {
                                    return r.daysWorked();
                                }
                                if (r.hoursWorked() != null) {
                                    return r.hoursWorked() / 8.0;
                                }
                                return 0.0;
                            })
                            .sum();
                    double outputPerDay = totalDays > 0 ? totalQty.doubleValue() / totalDays : 0.0;

                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("activityId", list.get(0).activityId().toString());
                    node.put("resourceId", list.get(0).resourceId().toString());
                    node.put("recordCount", list.size());
                    node.put("totalQtyExecuted", totalQty.doubleValue());
                    node.put("totalHoursWorked", totalHours);
                    node.put("totalDaysWorked", totalDays);
                    node.put("outputPerDay", outputPerDay);
                    return node;
                })
                .sorted(Comparator.comparingDouble((ObjectNode n) -> n.get("totalQtyExecuted").asDouble()).reversed())
                .limit(10)
                .forEach(pairs::add);

        BigDecimal overallQty = rows.stream()
                .map(DailyActivityResourceOutputResponse::qtyExecuted)
                .filter(q -> q != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        root.put("overallTotalQtyExecuted", overallQty.doubleValue());

        return root;
    }

    @Override
    public String tabKey() {
        return "daily-outputs";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to improve resource productivity and output tracking. Focus on under-utilized resources, high-performing activity-resource pairs, output trends, and quantity variance.";
    }
}
