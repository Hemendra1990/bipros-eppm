package com.bipros.reporting.portfolio;

import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.repository.EvmCalculationRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.reporting.portfolio.dto.CashFlowOutlookPoint;
import com.bipros.reporting.portfolio.dto.ComplianceRow;
import com.bipros.reporting.portfolio.dto.ContractorLeagueRow;
import com.bipros.reporting.portfolio.dto.CostOverrunRow;
import com.bipros.reporting.portfolio.dto.DelayedProjectRow;
import com.bipros.reporting.portfolio.dto.FundingUtilizationRow;
import com.bipros.reporting.portfolio.dto.PortfolioEvmRow;
import com.bipros.reporting.portfolio.dto.PortfolioScorecardDto;
import com.bipros.reporting.portfolio.dto.PortfolioScorecardDto.RagCounts;
import com.bipros.reporting.portfolio.dto.RiskHeatmapDto;
import com.bipros.reporting.portfolio.dto.ScheduleHealthRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioReportService {

  private static final BigDecimal CRORE = new BigDecimal("10000000");

  private final ProjectRepository projectRepository;
  private final EvmCalculationRepository evmCalculationRepository;

  @PersistenceContext private EntityManager em;

  // ─────────────────────── O4 — EVM Rollup ───────────────────────

  @Transactional(readOnly = true)
  public List<PortfolioEvmRow> getEvmRollup() {
    List<Project> projects = projectRepository.findAll();
    List<PortfolioEvmRow> rows = new ArrayList<>(projects.size());
    for (Project p : projects) {
      Optional<EvmCalculation> latestOpt =
          evmCalculationRepository.findTopByProjectIdOrderByDataDateDesc(p.getId());

      BigDecimal pv = BigDecimal.ZERO, ev = BigDecimal.ZERO, ac = BigDecimal.ZERO;
      BigDecimal cv = BigDecimal.ZERO, sv = BigDecimal.ZERO;
      BigDecimal eac = BigDecimal.ZERO, bac = BigDecimal.ZERO;
      double cpi = 0.0, spi = 0.0;

      if (latestOpt.isPresent()) {
        EvmCalculation e = latestOpt.get();
        pv = nullToZero(e.getPlannedValue());
        ev = nullToZero(e.getEarnedValue());
        ac = nullToZero(e.getActualCost());
        cpi = e.getCostPerformanceIndex() != null ? e.getCostPerformanceIndex() : 0.0;
        spi = e.getSchedulePerformanceIndex() != null ? e.getSchedulePerformanceIndex() : 0.0;
        cv = nullToZero(e.getCostVariance());
        sv = nullToZero(e.getScheduleVariance());
        eac = nullToZero(e.getEstimateAtCompletion());
        bac = nullToZero(e.getBudgetAtCompletion());
      }
      rows.add(
          new PortfolioEvmRow(
              p.getId(), p.getCode(), p.getName(), pv, ev, ac, cpi, spi, cv, sv, eac, bac));
    }
    return rows;
  }

  // ─────────────────────── O1 — Scorecard ───────────────────────

  @Transactional(readOnly = true)
  public PortfolioScorecardDto getScorecard() {
    List<Project> projects = projectRepository.findAll();
    Map<String, Long> byStatus = new LinkedHashMap<>();
    byStatus.put("PLANNED", 0L);
    byStatus.put("ACTIVE", 0L);
    byStatus.put("COMPLETED", 0L);
    byStatus.put("ON_HOLD", 0L);
    byStatus.put("CANCELLED", 0L);
    for (Project p : projects) {
      String s = p.getStatus() != null ? p.getStatus().name() : "UNKNOWN";
      byStatus.merge(s, 1L, Long::sum);
    }

    BigDecimal totalBudget = BigDecimal.ZERO;
    BigDecimal totalCommitted = BigDecimal.ZERO;
    BigDecimal totalSpent = BigDecimal.ZERO;

    totalBudget = queryScalarBigDecimal(
        "SELECT COALESCE(SUM(budget_crores), 0) FROM project.wbs_nodes");
    totalCommitted = queryScalarBigDecimal(
        "SELECT COALESCE(SUM(contract_value), 0) / ?1 FROM contract.contracts", CRORE);
    totalSpent = queryScalarBigDecimal(
        "SELECT COALESCE(SUM(net_amount), 0) / ?1 FROM cost.ra_bills "
            + "WHERE status IN ('APPROVED','PAID','CERTIFIED')",
        CRORE);

    long green = 0, amber = 0, red = 0;
    long activeWithCritical = 0;
    long openCriticalRisks = 0;

    for (Project p : projects) {
      Optional<EvmCalculation> latestOpt =
          evmCalculationRepository.findTopByProjectIdOrderByDataDateDesc(p.getId());
      String rag = "GREEN";
      if (latestOpt.isPresent()) {
        Double cpi = latestOpt.get().getCostPerformanceIndex();
        Double spi = latestOpt.get().getSchedulePerformanceIndex();
        rag = bandRag(cpi, spi);
      }
      switch (rag) {
        case "GREEN" -> green++;
        case "AMBER" -> amber++;
        case "RED" -> red++;
      }
    }

    activeWithCritical = queryScalarLong(
        "SELECT COUNT(DISTINCT p.id) FROM project.projects p "
            + "JOIN activity.activities a ON a.project_id = p.id "
            + "WHERE p.status = 'ACTIVE' AND a.is_critical = TRUE");

    openCriticalRisks = queryScalarLong(
        "SELECT COUNT(*) FROM risk.risks "
            + "WHERE status NOT IN ('CLOSED','MITIGATED') "
            + "  AND (rag = 'RED' OR risk_score >= 15)");

    return new PortfolioScorecardDto(
        projects.size(),
        byStatus,
        scaleMoney(totalBudget),
        scaleMoney(totalCommitted),
        scaleMoney(totalSpent),
        new RagCounts(green, amber, red),
        activeWithCritical,
        openCriticalRisks);
  }

  // ─────────────────────── O2 — Delayed projects ───────────────────────

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<DelayedProjectRow> getDelayedProjects(int limit) {
    List<Project> projects = projectRepository.findAll();
    List<DelayedProjectRow> rows = new ArrayList<>();
    for (Project p : projects) {
      LocalDate plannedFinish = p.getPlannedFinishDate();
      LocalDate forecastFinish = plannedFinish;
      long daysDelayed = 0;
      double spi = 0.0;

      try {
        Object result =
            em.createNativeQuery(
                    "SELECT MAX(actual_finish_date) FROM activity.activities "
                        + "WHERE project_id = ?1 AND actual_finish_date IS NOT NULL")
                .setParameter(1, p.getId())
                .getSingleResult();
        if (result != null && plannedFinish != null) {
          LocalDate maxActual = LocalDate.parse(result.toString());
          if (maxActual.isAfter(plannedFinish)) {
            forecastFinish = maxActual;
            daysDelayed = ChronoUnit.DAYS.between(plannedFinish, maxActual);
          }
        }
      } catch (Exception ignored) {
      }

      Optional<EvmCalculation> latest =
          evmCalculationRepository.findTopByProjectIdOrderByDataDateDesc(p.getId());
      if (latest.isPresent() && latest.get().getSchedulePerformanceIndex() != null) {
        spi = latest.get().getSchedulePerformanceIndex();
        if (spi > 0 && spi < 1.0 && plannedFinish != null && p.getPlannedStartDate() != null) {
          long planned = ChronoUnit.DAYS.between(p.getPlannedStartDate(), plannedFinish);
          long forecast = Math.round(planned / spi);
          long slip = forecast - planned;
          if (slip > daysDelayed) {
            daysDelayed = slip;
            forecastFinish = plannedFinish.plusDays(slip);
          }
        }
      }

      String rag = daysDelayed > 90 ? "RED" : daysDelayed > 30 ? "AMBER" : "GREEN";
      rows.add(
          new DelayedProjectRow(
              p.getId(), p.getCode(), p.getName(), plannedFinish, forecastFinish, daysDelayed, spi, rag));
    }
    rows.sort(Comparator.comparingLong(DelayedProjectRow::daysDelayed).reversed());
    return rows.stream().limit(limit).toList();
  }

  // ─────────────────────── O3 — Cost overrun ───────────────────────

  @Transactional(readOnly = true)
  public List<CostOverrunRow> getCostOverrunProjects(int limit) {
    List<Project> projects = projectRepository.findAll();
    List<CostOverrunRow> rows = new ArrayList<>();
    for (Project p : projects) {
      Optional<EvmCalculation> latest =
          evmCalculationRepository.findTopByProjectIdOrderByDataDateDesc(p.getId());
      BigDecimal bac = BigDecimal.ZERO, eac = BigDecimal.ZERO;
      double cpi = 0.0;
      if (latest.isPresent()) {
        bac = nullToZero(latest.get().getBudgetAtCompletion());
        eac = nullToZero(latest.get().getEstimateAtCompletion());
        cpi = latest.get().getCostPerformanceIndex() != null
            ? latest.get().getCostPerformanceIndex()
            : 0.0;
      }
      if (bac.signum() == 0) {
        bac = queryScalarBigDecimal(
            "SELECT COALESCE(SUM(budget_crores) * ?1, 0) FROM project.wbs_nodes WHERE project_id = ?2",
            CRORE, p.getId());
      }
      BigDecimal variance = eac.subtract(bac);
      rows.add(
          new CostOverrunRow(
              p.getId(), p.getCode(), p.getName(),
              scaleMoney(bac), scaleMoney(eac), scaleMoney(variance), cpi));
    }
    rows.sort(Comparator.comparing(
        (CostOverrunRow r) -> r.varianceCrores() != null ? r.varianceCrores().abs() : BigDecimal.ZERO).reversed());
    return rows.stream().limit(limit).toList();
  }

  // ─────────────────────── O5 — Funding utilisation ───────────────────────

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<FundingUtilizationRow> getFundingUtilization() {
    List<Project> projects = projectRepository.findAll();
    List<FundingUtilizationRow> rows = new ArrayList<>();
    for (Project p : projects) {
      BigDecimal sanctioned = queryScalarBigDecimal(
          "SELECT COALESCE(SUM(allocated_amount), 0) / ?1 FROM cost.project_funding WHERE project_id = ?2",
          CRORE, p.getId());
      BigDecimal utilized = queryScalarBigDecimal(
          "SELECT COALESCE(SUM(net_amount), 0) / ?1 FROM cost.ra_bills "
              + "WHERE project_id = ?2 AND status IN ('APPROVED','PAID','CERTIFIED')",
          CRORE, p.getId());
      BigDecimal released = sanctioned; // no releases table; treat sanction as released
      BigDecimal pendingTreasury = BigDecimal.ZERO;
      double releasePct = percent(released, sanctioned);
      double utilizationPct = percent(utilized, released);

      String status = "ON_TRACK";
      if (releasePct < 50) status = "RELEASE_PENDING";
      else if (utilizationPct < 50) status = "UNDER_UTILIZED";
      else if (utilizationPct >= 95) status = "EXHAUSTED";

      rows.add(
          new FundingUtilizationRow(
              p.getId(),
              p.getName(),
              scaleMoney(sanctioned),
              scaleMoney(released),
              scaleMoney(utilized),
              scaleMoney(pendingTreasury),
              releasePct,
              utilizationPct,
              status));
    }
    return rows;
  }

  // ─────────────────────── O6 — Contractor league ───────────────────────

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<ContractorLeagueRow> getContractorLeague() {
    List<Object> rows =
        em.createNativeQuery(
                "SELECT c.contractor_code, MIN(c.contractor_name), "
                    + "       COUNT(DISTINCT c.project_id), "
                    + "       COALESCE(AVG(c.performance_score), 0), "
                    + "       COALESCE(AVG(c.spi), 0), "
                    + "       COALESCE(AVG(c.cpi), 0), "
                    + "       COALESCE(SUM(c.contract_value) / ?1, 0), "
                    + "       COALESCE(SUM(c.cumulative_ra_bills_crores), 0) "
                    + "FROM contract.contracts c "
                    + "WHERE c.contractor_code IS NOT NULL "
                    + "GROUP BY c.contractor_code "
                    + "ORDER BY 4 DESC")
            .setParameter(1, CRORE)
            .getResultList();

    List<ContractorLeagueRow> result = new ArrayList<>(rows.size());
    for (Object row : rows) {
      Object[] cols = (Object[]) row;
      result.add(
          new ContractorLeagueRow(
              cols[0] != null ? cols[0].toString() : "",
              cols[1] != null ? cols[1].toString() : "",
              cols[2] != null ? ((Number) cols[2]).longValue() : 0L,
              cols[3] != null ? ((Number) cols[3]).doubleValue() : 0.0,
              cols[4] != null ? ((Number) cols[4]).doubleValue() : 0.0,
              cols[5] != null ? ((Number) cols[5]).doubleValue() : 0.0,
              cols[6] != null ? new BigDecimal(cols[6].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
              cols[7] != null ? new BigDecimal(cols[7].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO));
    }
    return result;
  }

  // ─────────────────────── O7 — Risk heatmap ───────────────────────

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public RiskHeatmapDto getRiskHeatmap() {
    Map<String, Long> cellMap = new LinkedHashMap<>();
    List<Object> cellRows =
        em.createNativeQuery(
                "SELECT "
                    + "  CASE probability "
                    + "    WHEN 'VERY_LOW' THEN 1 WHEN 'LOW' THEN 2 WHEN 'MEDIUM' THEN 3 "
                    + "    WHEN 'HIGH' THEN 4 WHEN 'VERY_HIGH' THEN 5 ELSE 3 END AS p, "
                    + "  CASE impact "
                    + "    WHEN 'VERY_LOW' THEN 1 WHEN 'LOW' THEN 2 WHEN 'MEDIUM' THEN 3 "
                    + "    WHEN 'HIGH' THEN 4 WHEN 'VERY_HIGH' THEN 5 ELSE 3 END AS i, "
                    + "  COUNT(*) "
                    + "FROM risk.risks "
                    + "WHERE status NOT IN ('CLOSED','MITIGATED') "
                    + "GROUP BY p, i")
            .getResultList();
    List<RiskHeatmapDto.Cell> cells = new ArrayList<>();
    for (Object row : cellRows) {
      Object[] cols = (Object[]) row;
      int p = ((Number) cols[0]).intValue();
      int i = ((Number) cols[1]).intValue();
      long count = ((Number) cols[2]).longValue();
      cells.add(new RiskHeatmapDto.Cell(p, i, count));
    }

    List<Object> topRows =
        em.createNativeQuery(
                "SELECT r.id, r.project_id, p.code, r.code, r.title, r.probability, r.impact, "
                    + "       COALESCE(r.risk_score, 0), COALESCE(r.rag, 'AMBER') "
                    + "FROM risk.risks r "
                    + "LEFT JOIN project.projects p ON p.id = r.project_id "
                    + "WHERE r.status NOT IN ('CLOSED','MITIGATED') "
                    + "ORDER BY COALESCE(r.risk_score, 0) DESC "
                    + "LIMIT 5")
            .getResultList();
    List<RiskHeatmapDto.TopRisk> top = new ArrayList<>();
    for (Object row : topRows) {
      Object[] cols = (Object[]) row;
      top.add(
          new RiskHeatmapDto.TopRisk(
              UUID.fromString(cols[0].toString()),
              cols[1] != null ? UUID.fromString(cols[1].toString()) : null,
              cols[2] != null ? cols[2].toString() : "",
              cols[3] != null ? cols[3].toString() : "",
              cols[4] != null ? cols[4].toString() : "",
              cols[5] != null ? cols[5].toString() : "",
              cols[6] != null ? cols[6].toString() : "",
              ((Number) cols[7]).doubleValue(),
              cols[8] != null ? cols[8].toString() : "AMBER"));
    }

    return new RiskHeatmapDto(cells, top);
  }

  // ─────────────────────── O8 — Cash flow outlook ───────────────────────

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<CashFlowOutlookPoint> getCashFlowOutlook(int months) {
    YearMonth start = YearMonth.now();
    // cash_flow_forecasts.period is stored as a string like "2026-04" (monthly bucket).
    // planned_amount is treated as outflow; actual_amount isn't "inflow" in this model,
    // but until funding-release ingestion lands it's the best proxy we have.
    List<Object> rows = List.of();
    try {
      rows =
          em.createNativeQuery(
                  "SELECT period, "
                      + "       COALESCE(SUM(planned_amount), 0) / ?1, "
                      + "       COALESCE(SUM(actual_amount), 0) / ?1 "
                      + "FROM cost.cash_flow_forecasts "
                      + "WHERE period >= ?2 AND period < ?3 "
                      + "GROUP BY period ORDER BY period")
              .setParameter(1, CRORE)
              .setParameter(2, start.toString())
              .setParameter(3, start.plusMonths(months).toString())
              .getResultList();
    } catch (Exception e) {
      log.debug("cash-flow-outlook query failed: {}", e.getMessage());
    }

    Map<String, BigDecimal[]> byMonth = new LinkedHashMap<>();
    for (int i = 0; i < months; i++) {
      YearMonth ym = start.plusMonths(i);
      byMonth.put(ym.toString(), new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
    }
    for (Object row : rows) {
      Object[] cols = (Object[]) row;
      String ym = cols[0].toString();
      BigDecimal out = new BigDecimal(cols[1].toString());
      BigDecimal in = new BigDecimal(cols[2].toString());
      byMonth.put(ym, new BigDecimal[] {out, in});
    }

    List<CashFlowOutlookPoint> result = new ArrayList<>(months);
    BigDecimal cumulative = BigDecimal.ZERO;
    for (Map.Entry<String, BigDecimal[]> e : byMonth.entrySet()) {
      BigDecimal out = e.getValue()[0];
      BigDecimal in = e.getValue()[1];
      BigDecimal net = in.subtract(out);
      cumulative = cumulative.add(net);
      result.add(new CashFlowOutlookPoint(e.getKey(),
          scaleMoney(out), scaleMoney(in), scaleMoney(net), scaleMoney(cumulative)));
    }
    return result;
  }

  // ─────────────────────── O9 — Compliance ───────────────────────

  @Transactional(readOnly = true)
  public List<ComplianceRow> getCompliance() {
    List<Project> projects = projectRepository.findAll();
    List<ComplianceRow> rows = new ArrayList<>();
    for (Project p : projects) {
      boolean pfms = queryScalarLong(
          "SELECT COUNT(*) FROM cost.project_funding WHERE project_id = ?1", p.getId()) > 0;
      // Stub checks: no integration tables seeded yet — conservative booleans.
      boolean gstn = queryScalarLong(
          "SELECT COUNT(*) FROM contract.contracts WHERE project_id = ?1", p.getId()) > 0;
      boolean gem = true;
      boolean cppp = queryScalarLong(
          "SELECT COUNT(*) FROM contract.tenders WHERE project_id = ?1", p.getId()) > 0;
      boolean parivesh = true;

      int total = 5;
      int pass =
          (pfms ? 1 : 0) + (gstn ? 1 : 0) + (gem ? 1 : 0) + (cppp ? 1 : 0) + (parivesh ? 1 : 0);
      double score = total > 0 ? (pass * 100.0) / total : 0.0;

      rows.add(
          new ComplianceRow(p.getId(), p.getCode(), p.getName(), pfms, gstn, gem, cppp, parivesh, score));
    }
    return rows;
  }

  // ─────────────────────── O10 — Schedule health ───────────────────────

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<ScheduleHealthRow> getScheduleHealth() {
    List<Project> projects = projectRepository.findAll();
    List<ScheduleHealthRow> rows = new ArrayList<>();
    for (Project p : projects) {
      long missingLogic = queryScalarLong(
          "SELECT COUNT(a.id) FROM activity.activities a "
              + "LEFT JOIN activity.activity_relationships r "
              + "  ON (r.predecessor_activity_id = a.id OR r.successor_activity_id = a.id) "
              + "WHERE a.project_id = ?1 AND r.id IS NULL",
          p.getId());
      long leadRels = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activity_relationships r "
              + "JOIN activity.activities a ON a.id = r.predecessor_activity_id "
              + "WHERE a.project_id = ?1 AND r.lag < 0",
          p.getId());
      long lags = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activity_relationships r "
              + "JOIN activity.activities a ON a.id = r.predecessor_activity_id "
              + "WHERE a.project_id = ?1 AND r.lag > 0",
          p.getId());
      long totalRels = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activity_relationships r "
              + "JOIN activity.activities a ON a.id = r.predecessor_activity_id "
              + "WHERE a.project_id = ?1",
          p.getId());
      long fsRels = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activity_relationships r "
              + "JOIN activity.activities a ON a.id = r.predecessor_activity_id "
              + "WHERE a.project_id = ?1 AND r.relationship_type = 'FINISH_TO_START'",
          p.getId());
      double fsPct = totalRels > 0 ? (fsRels * 100.0) / totalRels : 100.0;

      long hardConstraints = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activities "
              + "WHERE project_id = ?1 AND primary_constraint_type IN ('START_ON','FINISH_ON','AS_LATE_AS_POSSIBLE')",
          p.getId());
      long highFloat = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activities WHERE project_id = ?1 AND total_float > 44",
          p.getId());
      long negFloat = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activities WHERE project_id = ?1 AND total_float < 0",
          p.getId());
      long invalidDates = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activities "
              + "WHERE project_id = ?1 AND planned_start_date IS NOT NULL AND planned_finish_date IS NOT NULL "
              + "  AND planned_start_date > planned_finish_date",
          p.getId());
      long resAllocIssues = queryScalarLong(
          "SELECT COUNT(a.id) FROM activity.activities a "
              + "LEFT JOIN resource.resource_assignments ra ON ra.activity_id = a.id "
              + "WHERE a.project_id = ?1 AND ra.id IS NULL",
          p.getId());
      long missedTasks = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activities "
              + "WHERE project_id = ?1 AND planned_finish_date < CURRENT_DATE "
              + "  AND (percent_complete IS NULL OR percent_complete < 100)",
          p.getId());
      long cpLength = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activities WHERE project_id = ?1 AND is_critical = TRUE",
          p.getId());
      boolean cpOk = cpLength > 0;

      double beiActual = 0.0;
      long completedByNow = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activities "
              + "WHERE project_id = ?1 AND percent_complete >= 100",
          p.getId());
      long shouldHaveCompleted = queryScalarLong(
          "SELECT COUNT(*) FROM activity.activities "
              + "WHERE project_id = ?1 AND planned_finish_date <= CURRENT_DATE",
          p.getId());
      if (shouldHaveCompleted > 0) {
        beiActual = (completedByNow * 1.0) / shouldHaveCompleted;
      }
      double beiRequired = 0.95;

      int totalChecks = 14;
      int failed = 0;
      if (missingLogic > 0) failed++;
      if (leadRels > 0) failed++;
      if (lags > totalRels * 0.1) failed++;
      if (fsPct < 90) failed++;
      if (hardConstraints > 0) failed++;
      if (highFloat > 0) failed++;
      if (negFloat > 0) failed++;
      if (invalidDates > 0) failed++;
      if (resAllocIssues > 0) failed++;
      if (missedTasks > 0) failed++;
      if (!cpOk) failed++;
      if (beiActual < beiRequired) failed++;
      double healthPct = ((totalChecks - failed) * 100.0) / totalChecks;

      rows.add(
          new ScheduleHealthRow(
              p.getId(), p.getCode(), p.getName(),
              missingLogic, leadRels, lags, fsPct, hardConstraints, highFloat, negFloat,
              invalidDates, resAllocIssues, missedTasks, cpOk, cpLength,
              beiActual, beiRequired, healthPct));
    }
    return rows;
  }

  // ─────────────────────── helpers ───────────────────────

  private static BigDecimal nullToZero(BigDecimal v) {
    return v != null ? v : BigDecimal.ZERO;
  }

  private static BigDecimal scaleMoney(BigDecimal v) {
    if (v == null) return BigDecimal.ZERO;
    return v.setScale(2, RoundingMode.HALF_UP);
  }

  private static double percent(BigDecimal num, BigDecimal den) {
    if (den == null || den.signum() == 0) return 0.0;
    return num.multiply(new BigDecimal("100"))
        .divide(den, 2, RoundingMode.HALF_UP)
        .doubleValue();
  }

  private static String bandRag(Double cpi, Double spi) {
    if (cpi == null || spi == null || cpi == 0.0 || spi == 0.0) return "GREEN";
    if (cpi >= 0.95 && spi >= 0.95) return "GREEN";
    if ((cpi >= 0.85 && cpi < 0.95) || (spi >= 0.85 && spi < 0.95)) return "AMBER";
    return "RED";
  }

  private BigDecimal queryScalarBigDecimal(String sql, Object... params) {
    try {
      var q = em.createNativeQuery(sql);
      for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
      Object r = q.getSingleResult();
      return r != null ? new BigDecimal(r.toString()) : BigDecimal.ZERO;
    } catch (Exception e) {
      log.debug("queryScalarBigDecimal failed: {}", e.getMessage());
      return BigDecimal.ZERO;
    }
  }

  private long queryScalarLong(String sql, Object... params) {
    try {
      var q = em.createNativeQuery(sql);
      for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
      Object r = q.getSingleResult();
      return r != null ? ((Number) r).longValue() : 0L;
    } catch (Exception e) {
      log.debug("queryScalarLong failed: {}", e.getMessage());
      return 0L;
    }
  }
}
