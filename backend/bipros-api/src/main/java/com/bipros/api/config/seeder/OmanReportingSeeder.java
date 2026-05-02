package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.reporting.domain.model.DashboardConfig;
import com.bipros.reporting.domain.model.KpiDefinition;
import com.bipros.reporting.domain.model.KpiNodeSnapshot;
import com.bipros.reporting.domain.model.KpiSnapshot;
import com.bipros.reporting.domain.model.MonthlyEvmSnapshot;
import com.bipros.reporting.domain.model.ReportDefinition;
import com.bipros.reporting.domain.model.ReportExecution;
import com.bipros.reporting.domain.model.ReportFormat;
import com.bipros.reporting.domain.model.ReportStatus;
import com.bipros.reporting.domain.model.ReportType;
import com.bipros.reporting.domain.repository.DashboardConfigRepository;
import com.bipros.reporting.domain.repository.KpiDefinitionRepository;
import com.bipros.reporting.domain.repository.KpiNodeSnapshotRepository;
import com.bipros.reporting.domain.repository.KpiSnapshotRepository;
import com.bipros.reporting.domain.repository.MonthlyEvmSnapshotRepository;
import com.bipros.reporting.domain.repository.ReportDefinitionRepository;
import com.bipros.reporting.domain.repository.ReportExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("seed")
@Order(154)
@RequiredArgsConstructor
public class OmanReportingSeeder implements CommandLineRunner {

    private static final String PROJECT_CODE = "6155";
    private static final long DETERMINISTIC_SEED = 6155L;
    private static final LocalDate DEFAULT_DATA_DATE = LocalDate.of(2026, 4, 29);

    private final ProjectRepository projectRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiSnapshotRepository kpiSnapshotRepository;
    private final KpiNodeSnapshotRepository kpiNodeSnapshotRepository;
    private final MonthlyEvmSnapshotRepository monthlyEvmSnapshotRepository;
    private final DashboardConfigRepository dashboardConfigRepository;
    private final ReportDefinitionRepository reportDefinitionRepository;
    private final ReportExecutionRepository reportExecutionRepository;

    private static final String[][] KPI_DEFS = {
        {"SPI",                   "Schedule Performance Index",     "BCWP / BCWS",                   "ratio",  "0.95", "0.85", "0.70", "SCHEDULING",  "HIGHER_BETTER"},
        {"CPI",                   "Cost Performance Index",         "BCWP / ACWP",                   "ratio",  "0.95", "0.85", "0.70", "COST",        "HIGHER_BETTER"},
        {"SCH_VARIANCE",          "Schedule Variance",              "BCWP - BCWS",                   "OMR",    "0",    "-5",   "-15",  "SCHEDULING",  "HIGHER_BETTER"},
        {"COST_VARIANCE",         "Cost Variance",                  "BCWP - ACWP",                   "OMR",    "0",    "-5",   "-15",  "COST",        "HIGHER_BETTER"},
        {"RESOURCE_UTIL",         "Resource Utilisation",           "Deployed / Planned × 100",      "%",      "90",   "75",   "60",   "RESOURCE",    "HIGHER_BETTER"},
        {"SAFETY_INCIDENT_RATE",  "Safety Incident Rate",           "Incidents / 100k man-hours",    "rate",   "0",    "2",    "5",    "HSE",         "LOWER_BETTER"},
        {"QUALITY_NCR_COUNT",     "Quality NCR Count",              "Open NCRs",                     "count",  "0",    "5",    "10",   "QUALITY",     "LOWER_BETTER"},
        {"RFI_CLOSURE_RATE",      "RFI Closure Rate",               "Closed / Total × 100",          "%",      "90",   "75",   "60",   "DOCUMENT",    "HIGHER_BETTER"},
        {"DOC_APPROVAL_CYCLE",    "Document Approval Cycle",        "Avg days to approve",           "days",   "3",    "7",    "14",   "DOCUMENT",    "LOWER_BETTER"},
        {"WEATHER_DELAY_DAYS",    "Weather Delay Days",             "Days lost to weather",          "days",   "0",    "3",    "7",    "SCHEDULING",  "LOWER_BETTER"},
        {"EQUIP_AVAILABILITY",    "Equipment Availability",         "Available / Total × 100",       "%",      "90",   "80",   "65",   "RESOURCE",    "HIGHER_BETTER"},
        {"LABOUR_PRODUCTIVITY",   "Labour Productivity",            "Actual output / Norm output",   "ratio",  "0.90", "0.75", "0.60", "RESOURCE",    "HIGHER_BETTER"},
    };

    @Override
    @Transactional
    public void run(String... args) {
        Optional<Project> projectOpt = projectRepository.findByCode(PROJECT_CODE);
        if (projectOpt.isEmpty()) {
            log.warn("[BNK-REPORTING] project '{}' not found — skipping", PROJECT_CODE);
            return;
        }
        Project project = projectOpt.get();
        UUID projectId = project.getId();

        if (kpiDefinitionRepository.findByCode("SPI").isPresent()) {
            log.info("[BNK-REPORTING] KPI definitions already seeded — skipping");
            return;
        }

        Random rng = new Random(DETERMINISTIC_SEED);
        LocalDate dataDate = project.getDataDate() != null ? project.getDataDate() : DEFAULT_DATA_DATE;

        List<WbsNode> wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);
        List<WbsNode> leafNodes = wbsNodes.stream()
            .filter(n -> wbsNodes.stream().noneMatch(c -> n.getId().equals(c.getParentId())))
            .toList();

        List<KpiDefinition> kpis = seedKpiDefinitions();
        int snapCount = seedKpiSnapshots(kpis, projectId, dataDate, rng);
        int nodeSnapCount = seedKpiNodeSnapshots(kpis, projectId, dataDate, rng);
        int evmCount = seedMonthlyEvmSnapshots(projectId, leafNodes, dataDate, rng);
        seedDashboards();
        List<ReportDefinition> reports = seedReportDefinitions();
        int execCount = seedReportExecutions(reports, projectId);

        log.info("[BNK-REPORTING] Seeded {} KPI definitions, {} snapshots, {} node snapshots, "
                + "{} monthly EVM snapshots, 5 dashboards, {} reports, {} executions",
            kpis.size(), snapCount, nodeSnapCount, evmCount, reports.size(), execCount);
    }

    private List<KpiDefinition> seedKpiDefinitions() {
        List<KpiDefinition> kpis = new java.util.ArrayList<>();
        for (String[] def : KPI_DEFS) {
            KpiDefinition kpi = new KpiDefinition();
            kpi.setCode(def[0]);
            kpi.setName(def[1]);
            kpi.setFormula(def[2]);
            kpi.setUnit(def[3]);
            kpi.setGreenThreshold(Double.parseDouble(def[4]));
            kpi.setAmberThreshold(Double.parseDouble(def[5]));
            kpi.setRedThreshold(Double.parseDouble(def[6]));
            kpi.setModuleSource(def[7]);
            kpi.setIsActive(Boolean.TRUE);
            kpi.setTargetValue(Double.parseDouble(def[4]));
            kpi.setDirection(def[8]);
            kpis.add(kpiDefinitionRepository.save(kpi));
        }
        return kpis;
    }

    private int seedKpiSnapshots(List<KpiDefinition> kpis, UUID projectId, LocalDate dataDate, Random rng) {
        int weeks = 12;
        int created = 0;
        for (KpiDefinition kpi : kpis) {
            for (int w = weeks - 1; w >= 0; w--) {
                LocalDate weekDate = dataDate.minusWeeks(w);
                String period = weekDate.getYear() + "-W" + String.format("%02d", weekDate.getDayOfYear() / 7 + 1);
                double base = kpi.getGreenThreshold() != null ? kpi.getGreenThreshold() : 1.0;
                double noise = (rng.nextDouble() - 0.3) * 0.3;
                double value = Math.max(0, base + noise);
                KpiSnapshot.KpiStatus status = deriveKpiStatus(kpi, value);

                KpiSnapshot snap = new KpiSnapshot();
                snap.setKpiDefinitionId(kpi.getId());
                snap.setProjectId(projectId);
                snap.setPeriod(period);
                snap.setValue(round3(value));
                snap.setStatus(status);
                snap.setCalculatedAt(weekDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
                kpiSnapshotRepository.save(snap);
                created++;
            }
        }
        return created;
    }

    private int seedKpiNodeSnapshots(List<KpiDefinition> kpis, UUID projectId, LocalDate dataDate, Random rng) {
        String[] nodeCodes = {"BNK-S1", "BNK-S2", "BNK-S3", "BNK-S4", "PROGRAMME"};
        String[] topKpiCodes = {"SPI", "CPI", "SCH_VARIANCE", "COST_VARIANCE", "RESOURCE_UTIL"};
        int created = 0;

        for (String kpiCode : topKpiCodes) {
            KpiDefinition kpi = kpis.stream().filter(k -> k.getCode().equals(kpiCode)).findFirst().orElse(null);
            if (kpi == null) continue;

            for (String nodeCode : nodeCodes) {
                double base = kpi.getGreenThreshold() != null ? kpi.getGreenThreshold() : 1.0;
                double noise = (rng.nextDouble() - 0.3) * 0.25;
                double value = Math.max(0, base + noise);
                KpiSnapshot.KpiStatus rag = deriveKpiStatus(kpi, value);

                KpiNodeSnapshot snap = KpiNodeSnapshot.builder()
                    .kpiDefinitionId(kpi.getId())
                    .kpiCode(kpiCode)
                    .nodeCode(nodeCode)
                    .period(String.valueOf(dataDate.getYear()))
                    .value(round3(value))
                    .targetValue(kpi.getTargetValue())
                    .rag(rag)
                    .calculatedAt(dataDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC))
                    .build();
                kpiNodeSnapshotRepository.save(snap);
                created++;
            }
        }
        return created;
    }

    private int seedMonthlyEvmSnapshots(UUID projectId, List<WbsNode> leafNodes, LocalDate dataDate, Random rng) {
        if (leafNodes.isEmpty()) return 0;
        int created = 0;
        for (int m = 0; m < 4; m++) {
            LocalDate reportMonth = LocalDate.of(2026, 1 + m, 1);
            for (WbsNode node : leafNodes) {
                double bacVal = 500_000 + rng.nextInt(2_000_000);
                double pctComplete = Math.min(1.0, (m + 1) * 0.15 + rng.nextDouble() * 0.1);
                double bcws = bacVal * Math.min(1.0, (m + 1) * 0.20);
                double bcwp = bacVal * pctComplete;
                double acwp = bcwp * (0.90 + rng.nextDouble() * 0.25);
                double spi = bcws > 0 ? bcwp / bcws : 1.0;
                double cpi = acwp > 0 ? bcwp / acwp : 1.0;
                double sv = bcwp - bcws;
                double cv = bcwp - acwp;
                double eac = cpi > 0 ? bacVal / cpi : bacVal;
                double etc = eac - acwp;

                MonthlyEvmSnapshot.ScheduleStatus scheduleStatus = MonthlyEvmSnapshot.scheduleStatusFromSpi(
                    BigDecimal.valueOf(spi).setScale(3, RoundingMode.HALF_UP));

                MonthlyEvmSnapshot snap = MonthlyEvmSnapshot.builder()
                    .projectId(projectId)
                    .nodeId(node.getId())
                    .nodeCode(node.getCode())
                    .reportMonth(reportMonth)
                    .bcws(BigDecimal.valueOf(bcws).setScale(2, RoundingMode.HALF_UP))
                    .bcwp(BigDecimal.valueOf(bcwp).setScale(2, RoundingMode.HALF_UP))
                    .acwp(BigDecimal.valueOf(acwp).setScale(2, RoundingMode.HALF_UP))
                    .bac(BigDecimal.valueOf(bacVal).setScale(2, RoundingMode.HALF_UP))
                    .spi(BigDecimal.valueOf(spi).setScale(3, RoundingMode.HALF_UP))
                    .cpi(BigDecimal.valueOf(cpi).setScale(3, RoundingMode.HALF_UP))
                    .eac(BigDecimal.valueOf(eac).setScale(2, RoundingMode.HALF_UP))
                    .etc(BigDecimal.valueOf(etc).setScale(2, RoundingMode.HALF_UP))
                    .cv(BigDecimal.valueOf(cv).setScale(2, RoundingMode.HALF_UP))
                    .sv(BigDecimal.valueOf(sv).setScale(2, RoundingMode.HALF_UP))
                    .pctCompleteAi(BigDecimal.valueOf(pctComplete * 100).setScale(2, RoundingMode.HALF_UP))
                    .pctCompleteContractor(BigDecimal.valueOf(Math.min(100, pctComplete * 100 + 3)).setScale(2, RoundingMode.HALF_UP))
                    .scheduleStatus(scheduleStatus)
                    .redRisksCount(rng.nextInt(4))
                    .openRaBillsCrores(BigDecimal.valueOf(rng.nextDouble() * 2).setScale(2, RoundingMode.HALF_UP))
                    .mprStatus("SUBMITTED")
                    .build();
                monthlyEvmSnapshotRepository.save(snap);
                created++;
            }
        }
        return created;
    }

    private void seedDashboards() {
        seedDashboard(DashboardConfig.DashboardTier.EXECUTIVE, "Executive Dashboard", true,
            "{\"tiles\":[\"kpi-summary\",\"risk-heatmap\",\"evm-chart\",\"cash-flow\"],\"layout\":\"2x2\"}");
        seedDashboard(DashboardConfig.DashboardTier.FIELD, "Field Operations Dashboard", false,
            "{\"tiles\":[\"daily-progress\",\"weather\",\"equipment-status\",\"safety\"],\"layout\":\"2x2\"}");
        seedDashboard(DashboardConfig.DashboardTier.OPERATIONAL, "Operational Dashboard", false,
            "{\"tiles\":[\"resource-histogram\",\"material-stock\",\"schedule-gantt\",\"quality-ncr\"],\"layout\":\"3x2\"}");
        seedDashboard(DashboardConfig.DashboardTier.PROGRAMME, "Programme Dashboard", false,
            "{\"tiles\":[\"portfolio-overview\",\"milestone-tracker\",\"kpi-trends\",\"risk-register\"],\"layout\":\"2x2\"}");
        seedDashboard(DashboardConfig.DashboardTier.PROJECT_MANAGER, "Portfolio Dashboard", false,
            "{\"tiles\":[\"project-comparison\",\"scoring-matrix\",\"funding-utilisation\",\"forecast\"],\"layout\":\"3x2\"}");
    }

    private void seedDashboard(DashboardConfig.DashboardTier tier, String name, boolean isDefault, String config) {
        if (dashboardConfigRepository.findByTier(tier).isPresent()) return;
        DashboardConfig d = new DashboardConfig();
        d.setTier(tier);
        d.setName(name);
        d.setIsDefault(isDefault);
        d.setLayoutConfig(config);
        dashboardConfigRepository.save(d);
    }

    private List<ReportDefinition> seedReportDefinitions() {
        List<ReportDefinition> reports = new java.util.ArrayList<>();
        reports.add(seedReport("Daily Progress Report", "Daily site progress with chainage, quantities, and weather",
            ReportType.TABULAR, true,
            "{\"columns\":[\"date\",\"supervisor\",\"chainage\",\"activity\",\"qty\",\"cumulative\"],\"filters\":[\"dateRange\",\"supervisor\"]}"));
        reports.add(seedReport("Monthly EVM Report", "Earned Value Management analysis with SPI/CPI trends and forecasts",
            ReportType.S_CURVE, true,
            "{\"charts\":[\"s-curve\",\"spi-cpi-trend\",\"variance\"],\"period\":\"monthly\"}"));
        reports.add(seedReport("Risk Register", "Full risk register with scoring matrix and response plans",
            ReportType.MATRIX, true,
            "{\"view\":\"matrix\",\"groupBy\":\"category\",\"showClosed\":false}"));
        reports.add(seedReport("Resource Histogram", "Equipment and labour deployment histogram by date",
            ReportType.RESOURCE_HISTOGRAM, true,
            "{\"resources\":[\"equipment\",\"labour\"],\"groupBy\":\"week\"}"));
        reports.add(seedReport("Cost S-Curve", "Planned vs actual cost S-curve with EAC/ETC projections",
            ReportType.S_CURVE, true,
            "{\"charts\":[\"planned\",\"actual\",\"eac\"],\"currency\":\"OMR\"}"));
        return reports;
    }

    private ReportDefinition seedReport(String name, String description, ReportType type, boolean builtIn, String config) {
        ReportDefinition r = new ReportDefinition();
        r.setName(name);
        r.setDescription(description);
        r.setReportType(type);
        r.setIsBuiltIn(builtIn);
        r.setConfigJson(config);
        return reportDefinitionRepository.save(r);
    }

    private int seedReportExecutions(List<ReportDefinition> reports, UUID projectId) {
        int created = 0;
        Instant now = Instant.now();
        for (ReportDefinition report : reports) {
            ReportExecution exec = new ReportExecution();
            exec.setReportDefinitionId(report.getId());
            exec.setProjectId(projectId);
            exec.setFormat(ReportFormat.PDF);
            exec.setStatus(ReportStatus.COMPLETED);
            exec.setParameters("{\"projectCode\":\"" + PROJECT_CODE + "\"}");
            exec.setResultData("{\"rows\":128,\"pages\":5}");
            exec.setFilePath("/storage/reports/" + report.getName().toLowerCase().replace(" ", "-") + ".pdf");
            exec.setExecutedAt(now.minusSeconds(3600));
            exec.setCompletedAt(now.minusSeconds(3500));
            reportExecutionRepository.save(exec);
            created++;
        }
        return created;
    }

    private KpiSnapshot.KpiStatus deriveKpiStatus(KpiDefinition kpi, double value) {
        double green = kpi.getGreenThreshold() != null ? kpi.getGreenThreshold() : 0;
        double amber = kpi.getAmberThreshold() != null ? kpi.getAmberThreshold() : 0;
        boolean lowerBetter = "LOWER_BETTER".equals(kpi.getDirection());
        if (lowerBetter) {
            if (value <= green) return KpiSnapshot.KpiStatus.GREEN;
            if (value <= amber) return KpiSnapshot.KpiStatus.AMBER;
            return KpiSnapshot.KpiStatus.RED;
        } else {
            if (value >= green) return KpiSnapshot.KpiStatus.GREEN;
            if (value >= amber) return KpiSnapshot.KpiStatus.AMBER;
            return KpiSnapshot.KpiStatus.RED;
        }
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
