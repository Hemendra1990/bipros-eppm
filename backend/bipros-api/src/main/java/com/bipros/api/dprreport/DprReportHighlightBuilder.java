package com.bipros.api.dprreport;

import com.bipros.ai.insights.dto.InsightHighlight;
import com.bipros.ai.insights.dto.InsightVariance;
import org.springframework.stereotype.Service;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DprReportHighlightBuilder {
    private static final DecimalFormat NUM = new DecimalFormat("#,##0");

    public List<InsightHighlight> highlights(DprReportMetrics m) {
        List<InsightHighlight> out = new ArrayList<>();
        out.add(new InsightHighlight("Approved DPRs", String.valueOf(m.totalDprs), "info", null));
        out.add(new InsightHighlight("Cost variance",
            NUM.format(Math.abs(m.totalCostVariance)) + " " + m.currencyCode + (m.totalCostVariance > 0 ? " over" : " under"),
            m.totalCostVariance > 0 ? "warning" : "info",
            m.totalCostVariance > 0 ? "up" : "down"));
        out.add(new InsightHighlight("Open issues", String.valueOf(m.openIssues),
            m.criticalIssues > 0 ? "critical" : (m.openIssues > 0 ? "warning" : "info"), null));
        m.roleEfficiencies.stream()
            .filter(r -> r.utilizationPct() != null)
            .min(Comparator.comparingDouble(r -> r.utilizationPct()))
            .ifPresent(worst -> out.add(new InsightHighlight(
                "Lowest efficiency", worst.role() + " " + NUM.format(worst.utilizationPct()) + "%",
                worst.severity(), "down")));
        if (m.safetyIncidents > 0) {
            out.add(new InsightHighlight("Safety incidents", String.valueOf(m.safetyIncidents), "critical", "up"));
        }
        return out;
    }

    public List<InsightVariance> variances(DprReportMetrics m) {
        List<InsightVariance> out = new ArrayList<>();
        for (var r : m.roleEfficiencies) {
            if (r.utilizationPct() == null) continue;
            out.add(new InsightVariance(r.role(),
                (r.costImplication() > 0 ? "+" : "") + NUM.format(r.costImplication()) + " " + m.currencyCode,
                "Efficiency " + NUM.format(r.utilizationPct()) + "% vs norm"));
        }
        return out;
    }
}
