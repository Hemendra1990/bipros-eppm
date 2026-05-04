package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.reporting.domain.model.KpiDefinition;
import com.bipros.reporting.domain.model.KpiNodeSnapshot;
import com.bipros.reporting.domain.model.KpiSnapshot;
import com.bipros.reporting.domain.model.MonthlyEvmSnapshot;
import com.bipros.reporting.domain.repository.KpiDefinitionRepository;
import com.bipros.reporting.domain.repository.KpiNodeSnapshotRepository;
import com.bipros.reporting.domain.repository.MonthlyEvmSnapshotRepository;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskProbability;
import com.bipros.risk.domain.model.RiskRag;
import com.bipros.risk.domain.model.RiskStatus;
import com.bipros.risk.domain.model.RiskTrend;
import com.bipros.risk.domain.model.RiskType;
import com.bipros.risk.domain.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * IC-PMS Phase E seeder — M7 risks with RAG model + M9 monthly EVM snapshots +
 * 14 KPI definitions with per-node snapshots.
 *
 * <p>Seeds 24 risks (3 RED, 1 CRIMSON, 2 OPPORTUNITY, rest AMBER/GREEN), 9 monthly
 * EVM rows (Jul-2024 → Mar-2025 for DMIC-N03 rollup), 14 KPI definitions and
 * their per-node snapshots.
 *
 * <p>Sentinel: first risk {@code RISK-DMIC-001} present → skip.
 */
@Slf4j
@Component
@Profile("legacy-demo")
@Order(105)
@RequiredArgsConstructor
public class IcpmsPhaseESeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final RiskRepository riskRepository;
    private final KpiDefinitionRepository kpiDefinitionRepository;
    private final KpiNodeSnapshotRepository kpiNodeSnapshotRepository;
    private final MonthlyEvmSnapshotRepository monthlyEvmRepository;
    private final LegacyRiskCategoryLookup legacyCategoryLookup;

    @Override
    @Transactional
    public void run(String... args) {
        // Sentinel split so the Excel master-data loader owns risks while
        // KPI defs + monthly EVM still seed here. We check KPI-def population as the
        // true sentinel for "Phase E already ran" because risks may be loaded elsewhere.
        if (kpiDefinitionRepository.count() > 0) {
            log.info("[IC-PMS Phase E] KPIs/EVM already seeded, skipping");
            return;
        }
        Project programme = projectRepository.findByCode("DMIC-PROG").orElse(null);
        if (programme == null) {
            log.warn("[IC-PMS Phase E] DMIC-PROG project not found — run Phase A first");
            return;
        }
        UUID projectId = programme.getId();

        // Risks only seeded here if Excel loader didn't already populate them.
        if (riskRepository.count() == 0) {
            seedRisks(projectId);
        } else {
            log.info("[IC-PMS Phase E] risks already present (likely Excel loader), skipping risk seed");
        }
        seedKpiDefinitions();
        seedMonthlyEvm(projectId);
        seedKpiNodeSnapshots();
        verifyScenarios();

        log.info("[IC-PMS Phase E] seeded 14 KPI defs, 9 monthly EVM rows, per-node KPI snapshots");
    }

    // =========================================================================
    // M7 Risks
    // =========================================================================
    private void seedRisks(UUID projectId) {
        // Code, title, category, probability(1-5), impactCost(1-5), impactSchedule(1-5),
        // status, trend, opportunity?
        // Tuned: 1 CRIMSON (score ≥20), 3 RED (12-19), rest AMBER/GREEN, 2 OPPORTUNITY
        seedRisk(projectId, "RISK-DMIC-001", "Monsoon delay on DMIC-N03-P01 earthworks",
            "MONSOON_IMPACT", 4, 3, 5, RiskStatus.OPEN_ESCALATED,
            RiskTrend.WORSENING, false); // 4*5=20 → CRIMSON
        seedRisk(projectId, "RISK-DMIC-002", "Land acquisition dispute — DMIC-N04",
            "LAND_ACQUISITION", 4, 4, 4, RiskStatus.OPEN_UNDER_ACTIVE_MANAGEMENT,
            RiskTrend.STABLE, false); // 4*4=16 → RED
        seedRisk(projectId, "RISK-DMIC-003", "Contractor financial stress — EPC-003",
            "CONTRACTOR_FINANCIAL", 3, 5, 4, RiskStatus.OPEN_ESCALATED,
            RiskTrend.WORSENING, false); // 3*5=15 → RED
        seedRisk(projectId, "RISK-DMIC-004", "Statutory clearance delay — forest",
            "FOREST_CLEARANCE", 3, 3, 5, RiskStatus.OPEN_UNDER_ACTIVE_MANAGEMENT,
            RiskTrend.STABLE, false); // 3*5=15 → RED
        seedRisk(projectId, "RISK-DMIC-005", "Utility shifting delay — gas pipeline",
            "UTILITY_SHIFTING", 3, 2, 4, RiskStatus.OPEN_BEING_MANAGED,
            RiskTrend.IMPROVING, false); // 3*4=12 → RED
        seedRisk(projectId, "RISK-DMIC-006", "Steel price volatility",
            "MARKET_PRICE", 3, 3, 1, RiskStatus.OPEN_WATCH,
            RiskTrend.WORSENING, false); // 3*3=9 → AMBER
        seedRisk(projectId, "RISK-DMIC-007", "Cement supply disruption",
            "EXTERNAL", 2, 3, 2, RiskStatus.OPEN_MONITOR,
            RiskTrend.STABLE, false); // 2*3=6 → AMBER
        seedRisk(projectId, "RISK-DMIC-008", "Skilled labour availability",
            "RESOURCE", 3, 2, 3, RiskStatus.OPEN_BEING_MANAGED,
            RiskTrend.STABLE, false); // 3*3=9 → AMBER
        seedRisk(projectId, "RISK-DMIC-009", "Environmental compliance audit",
            "STATUTORY_CLEARANCE", 2, 2, 3, RiskStatus.OPEN_ASI_REVIEW,
            RiskTrend.IMPROVING, false); // 2*3=6 → AMBER
        seedRisk(projectId, "RISK-DMIC-010", "Tier-1 vendor delivery slip",
            "EXTERNAL", 2, 3, 2, RiskStatus.OPEN_TARGET,
            RiskTrend.STABLE, false); // 2*3=6 → AMBER
        seedRisk(projectId, "RISK-DMIC-011", "Design change — plant layout",
            "TECHNICAL", 2, 2, 2, RiskStatus.MITIGATING,
            RiskTrend.IMPROVING, false); // 2*2=4 → GREEN
        seedRisk(projectId, "RISK-DMIC-012", "Cyber-security DPIA gap — ICT",
            "TECHNOLOGY", 2, 2, 1, RiskStatus.OPEN_MONITOR,
            RiskTrend.STABLE, false); // 2*2=4 → GREEN
        seedRisk(projectId, "RISK-DMIC-013", "Local community engagement friction",
            "ORGANIZATIONAL", 2, 1, 2, RiskStatus.OPEN_BEING_MANAGED,
            RiskTrend.IMPROVING, false); // 2*2=4 → GREEN
        seedRisk(projectId, "RISK-DMIC-014", "Quality assurance lapse — pile caps",
            "QUALITY", 2, 2, 2, RiskStatus.MITIGATING,
            RiskTrend.IMPROVING, false); // 2*2=4 → GREEN
        seedRisk(projectId, "RISK-DMIC-015", "RA bill certification workflow latency",
            "PROJECT_MANAGEMENT", 2, 1, 2, RiskStatus.OPEN_WATCH,
            RiskTrend.STABLE, false); // 2*2=4 → GREEN
        seedRisk(projectId, "RISK-DMIC-016", "Geotech anomaly — DMIC-N05 foundation",
            "NATURAL_HAZARD", 2, 3, 3, RiskStatus.OPEN_UNDER_ACTIVE_MANAGEMENT,
            RiskTrend.STABLE, false); // 2*3=6 → AMBER
        seedRisk(projectId, "RISK-DMIC-017", "Cost escalation — bitumen VG30",
            "COST", 2, 2, 1, RiskStatus.MITIGATING,
            RiskTrend.STABLE, false); // 2*2=4 → GREEN
        seedRisk(projectId, "RISK-DMIC-018", "HVAC commissioning slippage",
            "SCHEDULE", 2, 1, 3, RiskStatus.OPEN_MONITOR,
            RiskTrend.STABLE, false); // 2*3=6 → AMBER
        seedRisk(projectId, "RISK-DMIC-019", "Piling rig availability — DMIC-N05",
            "RESOURCE", 2, 2, 2, RiskStatus.MITIGATING,
            RiskTrend.IMPROVING, false); // 2*2=4 → GREEN
        seedRisk(projectId, "RISK-DMIC-020", "Third-party quality audit findings",
            "QUALITY", 2, 2, 1, RiskStatus.OPEN_ASI_REVIEW,
            RiskTrend.STABLE, false); // 2*2=4 → GREEN
        seedRisk(projectId, "RISK-DMIC-021", "Performance bond renewal delay",
            "CONTRACTOR_FINANCIAL", 2, 3, 1, RiskStatus.OPEN_WATCH,
            RiskTrend.STABLE, false); // 2*3=6 → AMBER
        seedRisk(projectId, "RISK-DMIC-022", "Scope creep on ICT backbone",
            "PROJECT_MANAGEMENT", 3, 2, 2, RiskStatus.OPEN_BEING_MANAGED,
            RiskTrend.WORSENING, false); // 3*2=6 → AMBER
        // Opportunities (continue numbering per Excel M7_Risk_Register scheme)
        seedRisk(projectId, "RISK-DMIC-023", "Early completion bonus — DMIC-N06-P01",
            "SCHEDULE", 3, 4, 4, RiskStatus.OPEN_TARGET,
            RiskTrend.IMPROVING, true); // OPPORTUNITY
        seedRisk(projectId, "RISK-DMIC-024", "Bulk cement pricing leverage",
            "MARKET_PRICE", 3, 3, 1, RiskStatus.OPEN_TARGET,
            RiskTrend.IMPROVING, true); // OPPORTUNITY
    }

    private void seedRisk(UUID projectId, String code, String title, String legacyCategoryName,
                          int prob, int impactCost, int impactSched, RiskStatus status,
                          RiskTrend trend, boolean opportunity) {
        Risk risk = new Risk();
        risk.setProjectId(projectId);
        risk.setCode(code);
        risk.setTitle(title);
        risk.setCategory(legacyCategoryLookup.forLegacyEnum(legacyCategoryName));
        risk.setProbability(RiskProbability.values()[prob - 1]);
        risk.setImpactCost(impactCost);
        risk.setImpactSchedule(impactSched);
        risk.setStatus(status);
        risk.setTrend(trend);
        risk.setRiskType(opportunity ? RiskType.OPPORTUNITY : RiskType.THREAT);
        risk.setIdentifiedDate(LocalDate.of(2024, 9, 15));
        // Pre-response score: simple P × max(impactCost, impactSchedule) (matrix-driven scoring
        // happens at the service layer; seeder writes a baseline value that's recomputed when
        // the risk is later updated through RiskService).
        if (risk.getProbability() != null) {
            int p = risk.getProbability().ordinal() + 1;
            int i = Math.max(impactCost, impactSched);
            risk.setRiskScore((double) (p * i));
        }
        risk.setResidualRiskScore(risk.getRiskScore() != null ? risk.getRiskScore() * 0.7 : null);
        riskRepository.save(risk);
    }

    // =========================================================================
    // M9 KPI Definitions (14 canonical KPIs per Excel spec)
    // =========================================================================
    private void seedKpiDefinitions() {
        seedKpi("SPI", "Schedule Performance Index", "BCWP / BCWS", null, 0.95, 0.85, 1.0, "HIGHER_BETTER", "M2");
        seedKpi("CPI", "Cost Performance Index", "BCWP / ACWP", null, 0.95, 0.85, 1.0, "HIGHER_BETTER", "M2");
        seedKpi("PHYSICAL_PROGRESS", "Physical Progress %", "AI-derived from satellite", "%", 90.0, 75.0, 100.0, "HIGHER_BETTER", "M3");
        seedKpi("COST_VARIANCE", "Cost Variance %", "(BCWP - ACWP) / BCWP", "%", -5.0, -10.0, 0.0, "HIGHER_BETTER", "M2");
        seedKpi("SCHEDULE_VARIANCE", "Schedule Variance Days", "Planned vs actual", "days", -15.0, -45.0, 0.0, "HIGHER_BETTER", "M2");
        seedKpi("RED_RISKS_OPEN", "Red Risks Open", "count(rag=RED or CRIMSON)", "count", 2.0, 5.0, 0.0, "LOWER_BETTER", "M7");
        seedKpi("RA_BILLS_ON_HOLD", "RA Bills on Hold (Satellite Gate)", "count(gate in HOLD*)", "count", 1.0, 3.0, 0.0, "LOWER_BETTER", "M4");
        seedKpi("BG_EXPIRY_30D", "BG Expiring Within 30 Days", "count(bgExpiry within 30d)", "count", 1.0, 3.0, 0.0, "LOWER_BETTER", "M5");
        seedKpi("OPEN_RFIS", "Open RFIs", "count(status=OPEN)", "count", 5.0, 15.0, 0.0, "LOWER_BETTER", "M6");
        seedKpi("RESOURCE_UTIL_AVG", "Average Resource Utilisation", "avg(utilisationPercent)", "%", 75.0, 95.0, 80.0, "HIGHER_BETTER", "M8");
        seedKpi("RESOURCE_OVERUSE", "Resources Over 90% Utilised", "count(status=OVER_90 or CRITICAL_100)", "count", 5.0, 15.0, 0.0, "LOWER_BETTER", "M8");
        seedKpi("MPR_TIMELINESS", "MPR Published by 10th", "% of nodes reporting on time", "%", 90.0, 75.0, 100.0, "HIGHER_BETTER", "M9");
        seedKpi("DOC_APPROVAL_AGE", "Avg Days to Approve Drawings", "mean(days to IFC)", "days", 7.0, 14.0, 5.0, "LOWER_BETTER", "M6");
        seedKpi("SATELLITE_VARIANCE", "Satellite Variance AI vs Contractor", "|ai - claim|", "%", 5.0, 10.0, 0.0, "LOWER_BETTER", "M3");
    }

    private void seedKpi(String code, String name, String formula, String unit,
                         double green, double red, double target, String direction, String module) {
        KpiDefinition def = new KpiDefinition();
        def.setCode(code);
        def.setName(name);
        def.setFormula(formula);
        def.setUnit(unit);
        def.setGreenThreshold(green);
        def.setAmberThreshold((green + red) / 2.0);
        def.setRedThreshold(red);
        def.setTargetValue(target);
        def.setDirection(direction);
        def.setModuleSource(module);
        def.setIsActive(true);
        kpiDefinitionRepository.save(def);
    }

    // =========================================================================
    // M9 Monthly EVM (9 months × DMIC-N03 node)
    // =========================================================================
    private void seedMonthlyEvm(UUID projectId) {
        WbsNode n03 = wbsNodeRepository.findByCode("DMIC-N03").orElse(null);
        if (n03 == null) {
            log.warn("[IC-PMS Phase E] WBS DMIC-N03 not found — skipping monthly EVM seed");
            return;
        }
        // Month, BCWS, BCWP, ACWP, BAC, AI%, Claim%
        seedEvm(projectId, n03.getId(), "DMIC-N03", LocalDate.of(2024, 7, 1),
            bd("120.00"), bd("105.00"), bd("110.00"), bd("1500.00"), bd("7.00"), bd("8.50"), 1, bd("12.50"));
        seedEvm(projectId, n03.getId(), "DMIC-N03", LocalDate.of(2024, 8, 1),
            bd("240.00"), bd("215.00"), bd("220.00"), bd("1500.00"), bd("14.30"), bd("16.00"), 1, bd("14.20"));
        seedEvm(projectId, n03.getId(), "DMIC-N03", LocalDate.of(2024, 9, 1),
            bd("370.00"), bd("340.00"), bd("345.00"), bd("1500.00"), bd("22.60"), bd("24.50"), 2, bd("18.80"));
        seedEvm(projectId, n03.getId(), "DMIC-N03", LocalDate.of(2024, 10, 1),
            bd("510.00"), bd("475.00"), bd("480.00"), bd("1500.00"), bd("31.60"), bd("33.00"), 2, bd("25.40"));
        seedEvm(projectId, n03.getId(), "DMIC-N03", LocalDate.of(2024, 11, 1),
            bd("650.00"), bd("600.00"), bd("610.00"), bd("1500.00"), bd("40.00"), bd("42.00"), 2, bd("28.60"));
        seedEvm(projectId, n03.getId(), "DMIC-N03", LocalDate.of(2024, 12, 1),
            bd("790.00"), bd("720.00"), bd("735.00"), bd("1500.00"), bd("48.00"), bd("50.00"), 3, bd("32.80"));
        seedEvm(projectId, n03.getId(), "DMIC-N03", LocalDate.of(2025, 1, 1),
            bd("930.00"), bd("840.00"), bd("860.00"), bd("1500.00"), bd("56.00"), bd("58.50"), 3, bd("36.40"));
        seedEvm(projectId, n03.getId(), "DMIC-N03", LocalDate.of(2025, 2, 1),
            bd("1070.00"), bd("960.00"), bd("985.00"), bd("1500.00"), bd("64.00"), bd("67.00"), 3, bd("40.20"));
        seedEvm(projectId, n03.getId(), "DMIC-N03", LocalDate.of(2025, 3, 1),
            bd("1210.00"), bd("1080.00"), bd("1110.00"), bd("1500.00"), bd("72.00"), bd("75.00"), 3, bd("44.50"));
    }

    private void seedEvm(UUID projectId, UUID nodeId, String nodeCode, LocalDate month,
                         BigDecimal bcws, BigDecimal bcwp, BigDecimal acwp, BigDecimal bac,
                         BigDecimal aiPct, BigDecimal claimPct, int redRisks, BigDecimal openBills) {
        MonthlyEvmSnapshot m = new MonthlyEvmSnapshot();
        m.setProjectId(projectId);
        m.setNodeId(nodeId);
        m.setNodeCode(nodeCode);
        m.setReportMonth(month);
        m.setBcws(bcws);
        m.setBcwp(bcwp);
        m.setAcwp(acwp);
        m.setBac(bac);
        BigDecimal spi = bcws.signum() == 0 ? BigDecimal.ZERO
            : bcwp.divide(bcws, 3, java.math.RoundingMode.HALF_UP);
        BigDecimal cpi = acwp.signum() == 0 ? BigDecimal.ZERO
            : bcwp.divide(acwp, 3, java.math.RoundingMode.HALF_UP);
        m.setSpi(spi);
        m.setCpi(cpi);
        BigDecimal eac = cpi.signum() == 0 ? bac
            : bac.divide(cpi, 2, java.math.RoundingMode.HALF_UP);
        m.setEac(eac);
        m.setEtc(eac.subtract(acwp));
        m.setCv(bcwp.subtract(acwp));
        m.setSv(bcwp.subtract(bcws));
        m.setPctCompleteAi(aiPct);
        m.setPctCompleteContractor(claimPct);
        m.setScheduleStatus(MonthlyEvmSnapshot.scheduleStatusFromSpi(spi));
        m.setRedRisksCount(redRisks);
        m.setOpenRaBillsCrores(openBills);
        m.setMprStatus("PUBLISHED");
        monthlyEvmRepository.save(m);
    }

    // =========================================================================
    // M9 KPI Node Snapshots (per-node + programme rollup)
    // =========================================================================
    private void seedKpiNodeSnapshots() {
        List<String> nodes = List.of("DMIC-N03", "DMIC-N04", "DMIC-N05", "DMIC-N06", "DMIC-N08", "PROGRAMME");
        // Compact table: kpiCode, [n03, n04, n05, n06, n08, programme]
        seedKpiRow("SPI", new double[]{0.94, 0.82, 0.92, 1.10, 0.88, 0.93});
        seedKpiRow("CPI", new double[]{0.96, 0.85, 0.94, 1.08, 0.91, 0.95});
        seedKpiRow("PHYSICAL_PROGRESS", new double[]{72.00, 48.00, 35.80, 62.40, 5.00, 46.60});
        seedKpiRow("COST_VARIANCE", new double[]{-2.50, -8.80, -3.10, 1.50, -5.00, -3.60});
        seedKpiRow("SCHEDULE_VARIANCE", new double[]{-12.0, -42.0, -15.0, 8.0, -22.0, -16.6});
        seedKpiRow("RED_RISKS_OPEN", new double[]{2, 3, 1, 0, 1, 4});
        seedKpiRow("RA_BILLS_ON_HOLD", new double[]{1, 1, 1, 0, 0, 3});
        seedKpiRow("BG_EXPIRY_30D", new double[]{0, 1, 0, 0, 0, 1});
        seedKpiRow("OPEN_RFIS", new double[]{2, 1, 1, 0, 0, 4});
        seedKpiRow("RESOURCE_UTIL_AVG", new double[]{93.8, 85.0, 90.0, 78.0, 60.0, 88.0});
        seedKpiRow("RESOURCE_OVERUSE", new double[]{9, 6, 5, 3, 1, 24});
        seedKpiRow("MPR_TIMELINESS", new double[]{95.0, 90.0, 92.0, 100.0, 85.0, 92.5});
        seedKpiRow("DOC_APPROVAL_AGE", new double[]{6.5, 8.2, 5.8, 4.5, 9.0, 6.8});
        seedKpiRow("SATELLITE_VARIANCE", new double[]{7.00, 18.00, 10.80, 0.00, 5.00, 8.50});

        log.info("[IC-PMS Phase E] seeded {} KPI node snapshots across {} nodes",
            kpiNodeSnapshotRepository.count(), nodes.size());
    }

    private void seedKpiRow(String kpiCode, double[] values) {
        String[] nodes = {"DMIC-N03", "DMIC-N04", "DMIC-N05", "DMIC-N06", "DMIC-N08", "PROGRAMME"};
        KpiDefinition def = kpiDefinitionRepository.findByCode(kpiCode).orElse(null);
        if (def == null) {
            log.warn("[IC-PMS Phase E] KPI definition {} not found", kpiCode);
            return;
        }
        for (int i = 0; i < nodes.length && i < values.length; i++) {
            KpiNodeSnapshot s = new KpiNodeSnapshot();
            s.setKpiDefinitionId(def.getId());
            s.setKpiCode(kpiCode);
            s.setNodeCode(nodes[i]);
            s.setPeriod("2025-03");
            s.setValue(values[i]);
            s.setTargetValue(def.getTargetValue());
            s.setRag(bandRag(values[i], def));
            s.setCalculatedAt(Instant.now());
            kpiNodeSnapshotRepository.save(s);
        }
    }

    private KpiSnapshot.KpiStatus bandRag(double value, KpiDefinition def) {
        boolean higherBetter = "HIGHER_BETTER".equalsIgnoreCase(def.getDirection());
        double green = def.getGreenThreshold() != null ? def.getGreenThreshold() : 0;
        double red = def.getRedThreshold() != null ? def.getRedThreshold() : 0;
        if (higherBetter) {
            if (value >= green) return KpiSnapshot.KpiStatus.GREEN;
            if (value >= red) return KpiSnapshot.KpiStatus.AMBER;
            return KpiSnapshot.KpiStatus.RED;
        } else {
            if (value <= green) return KpiSnapshot.KpiStatus.GREEN;
            if (value <= red) return KpiSnapshot.KpiStatus.AMBER;
            return KpiSnapshot.KpiStatus.RED;
        }
    }

    // =========================================================================
    // Scenario verification — logs each README scenario's end state
    // =========================================================================
    private void verifyScenarios() {
        long crimson = riskRepository.findAll().stream()
            .filter(r -> r.getRag() == RiskRag.CRIMSON).count();
        long red = riskRepository.findAll().stream()
            .filter(r -> r.getRag() == RiskRag.RED).count();
        long opp = riskRepository.findAll().stream()
            .filter(r -> r.getRag() == RiskRag.OPPORTUNITY).count();
        log.info("[IC-PMS Scenarios] Risks: {} CRIMSON, {} RED, {} OPPORTUNITY",
            crimson, red, opp);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
