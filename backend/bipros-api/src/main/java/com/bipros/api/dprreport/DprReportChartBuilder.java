package com.bipros.api.dprreport;

import com.bipros.ai.insights.charts.EChartsOptions;
import com.bipros.ai.insights.dto.ChartSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DprReportChartBuilder {
    private final ObjectMapper objectMapper;

    public List<ChartSpec> charts(DprReportMetrics m) {
        List<ChartSpec> out = new ArrayList<>();

        // efficiency by role (bar) — required
        List<String> roles = m.roleEfficiencies.stream().filter(r -> r.utilizationPct() != null).map(r -> r.role()).toList();
        List<Number> effs = m.roleEfficiencies.stream().filter(r -> r.utilizationPct() != null)
            .map(r -> (Number) r.utilizationPct()).toList();
        if (!roles.isEmpty()) {
            out.add(new ChartSpec("dpr-efficiency", "Efficiency % by role", "bar",
                EChartsOptions.bar(objectMapper, roles, "Efficiency %", effs), null));
        }

        // cost budgeted vs actual — EChartsOptions.bar() supports one series across N categories,
        // not a true two-series grouped bar, so this is modelled as a single "Cost" series over
        // the two categories ["Budgeted", "Actual"] (cleanly supported, no helper changes needed).
        if (m.totalBudgetedCost != 0 || m.totalActualCost != 0) {
            out.add(new ChartSpec("dpr-cost-variance", "Cost: Budgeted vs Actual (" + m.currencyCode + ")", "bar",
                EChartsOptions.bar(objectMapper, List.of("Budgeted", "Actual"), "Cost",
                    List.of(m.totalBudgetedCost, m.totalActualCost)),
                null));
        }

        return out;
    }
}
