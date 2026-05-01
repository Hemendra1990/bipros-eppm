package com.bipros.ai.insights.periodperformance;

import com.bipros.ai.insights.InsightDataCollector;
import com.bipros.cost.application.dto.StorePeriodPerformanceDto;
import com.bipros.cost.application.service.CostService;
import com.bipros.cost.domain.entity.FinancialPeriod;
import com.bipros.cost.domain.repository.FinancialPeriodRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PeriodPerformanceInsightsCollector implements InsightDataCollector {

    private final CostService costService;
    private final FinancialPeriodRepository financialPeriodRepository;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode collect(UUID projectId) {
        List<StorePeriodPerformanceDto> records = costService.getProjectPeriodPerformance(projectId);

        Map<UUID, FinancialPeriod> periodMap = financialPeriodRepository.findAllByOrderBySortOrder().stream()
                .collect(Collectors.toMap(FinancialPeriod::getId, p -> p));

        Map<UUID, List<StorePeriodPerformanceDto>> byPeriod = records.stream()
                .collect(Collectors.groupingBy(StorePeriodPerformanceDto::financialPeriodId));

        List<Map<String, Object>> periodList = byPeriod.entrySet().stream()
                .sorted((e1, e2) -> {
                    FinancialPeriod p1 = periodMap.get(e1.getKey());
                    FinancialPeriod p2 = periodMap.get(e2.getKey());
                    int s1 = p1 != null && p1.getSortOrder() != null ? p1.getSortOrder() : 0;
                    int s2 = p2 != null && p2.getSortOrder() != null ? p2.getSortOrder() : 0;
                    return Integer.compare(s2, s1);
                })
                .limit(5)
                .map(e -> {
                    UUID periodId = e.getKey();
                    List<StorePeriodPerformanceDto> list = e.getValue();
                    FinancialPeriod period = periodMap.get(periodId);
                    String periodName = period != null ? period.getName() : periodId.toString();

                    BigDecimal actualCost = list.stream()
                            .map(r -> r.actualLaborCost() != null ? r.actualLaborCost() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .add(list.stream()
                                    .map(r -> r.actualNonlaborCost() != null ? r.actualNonlaborCost() : BigDecimal.ZERO)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                            .add(list.stream()
                                    .map(r -> r.actualMaterialCost() != null ? r.actualMaterialCost() : BigDecimal.ZERO)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                            .add(list.stream()
                                    .map(r -> r.actualExpenseCost() != null ? r.actualExpenseCost() : BigDecimal.ZERO)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add));

                    BigDecimal ev = list.stream()
                            .map(r -> r.earnedValueCost() != null ? r.earnedValueCost() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal pv = list.stream()
                            .map(r -> r.plannedValueCost() != null ? r.plannedValueCost() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    double laborUnits = list.stream()
                            .mapToDouble(r -> r.actualLaborUnits() != null ? r.actualLaborUnits() : 0.0)
                            .sum();

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("periodName", periodName);
                    m.put("actualCost", actualCost);
                    m.put("earnedValue", ev);
                    m.put("plannedValue", pv);
                    m.put("laborUnits", laborUnits);
                    return m;
                })
                .collect(Collectors.toList());

        BigDecimal totalActualCost = records.stream()
                .map(r -> r.actualLaborCost() != null ? r.actualLaborCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(records.stream()
                        .map(r -> r.actualNonlaborCost() != null ? r.actualNonlaborCost() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .add(records.stream()
                        .map(r -> r.actualMaterialCost() != null ? r.actualMaterialCost() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .add(records.stream()
                        .map(r -> r.actualExpenseCost() != null ? r.actualExpenseCost() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal totalEv = records.stream()
                .map(r -> r.earnedValueCost() != null ? r.earnedValueCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPv = records.stream()
                .map(r -> r.plannedValueCost() != null ? r.plannedValueCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double totalLaborUnits = records.stream()
                .mapToDouble(r -> r.actualLaborUnits() != null ? r.actualLaborUnits() : 0.0)
                .sum();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("totalActualCost", totalActualCost);
        snapshot.put("totalEarnedValue", totalEv);
        snapshot.put("totalPlannedValue", totalPv);
        snapshot.put("totalLaborUnits", totalLaborUnits);
        snapshot.put("topPeriods", periodList);

        return objectMapper.valueToTree(snapshot);
    }

    @Override
    public String tabKey() {
        return "period-performance";
    }

    @Override
    public String promptInstructions() {
        return "Produce insights to improve period performance and earned value tracking. Focus on labor/material/expense cost breakdowns, EV vs PV variance by period, and under-performing periods.";
    }
}
