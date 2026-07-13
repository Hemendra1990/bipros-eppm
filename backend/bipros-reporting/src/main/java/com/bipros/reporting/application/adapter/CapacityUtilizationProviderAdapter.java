package com.bipros.reporting.application.adapter;

import com.bipros.ai.agent.support.CapacityUtilizationProvider;
import com.bipros.reporting.application.dto.CapacityUtilizationReport;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.RolePeriod;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.RoleRow;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.Section;
import com.bipros.reporting.application.service.CapacityUtilizationReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dependency-inversion adapter: exposes the canonical {@link CapacityUtilizationReportService} to the
 * AI module through the {@link CapacityUtilizationProvider} port (defined in {@code bipros-ai}, which
 * {@code bipros-reporting} already depends on). Pure delegation + mapping — it adds no computation, so
 * the AI agent's per-role efficiency and cost overrun are byte-for-byte the Capacity Util. tab's.
 */
@Component
@RequiredArgsConstructor
public class CapacityUtilizationProviderAdapter implements CapacityUtilizationProvider {

    /** Far-past start so the report's "cumulative" period covers the whole project through today. */
    private static final LocalDate PROJECT_START = LocalDate.of(1970, 1, 1);

    private final CapacityUtilizationReportService reportService;

    @Override
    public List<RoleEfficiency> cumulativeByRole(UUID projectId) {
        // groupBy=ROLE, normType=null (both manpower + equipment), supervisorUserId=null (all supervisors),
        // workDays=26 (default). The cumulative RolePeriod on each role row is project-to-date.
        CapacityUtilizationReport report = reportService.build(
                projectId, PROJECT_START, LocalDate.now(), "ROLE", null, null, 26);
        List<RoleEfficiency> out = new ArrayList<>();
        collect(report.manpower(), "MANPOWER", out);
        collect(report.equipment(), "EQUIPMENT", out);
        return out;
    }

    private void collect(Section section, String type, List<RoleEfficiency> out) {
        if (section == null || section.rows() == null) {
            return;
        }
        for (RoleRow row : section.rows()) {
            RolePeriod c = row.cumulative();
            if (c == null) {
                continue;
            }
            out.add(new RoleEfficiency(
                    type, row.roleName(),
                    d(c.budgetDays()), d(c.actualDays()),
                    d(c.utilizationPct()), d(row.ratePerDay()), d(c.costImplication())));
        }
    }

    private static double d(BigDecimal b) {
        return b == null ? 0.0 : b.doubleValue();
    }
}
