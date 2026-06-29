package com.bipros.reporting.application.service;

import com.bipros.cost.application.dto.CostSummaryDto;
import com.bipros.cost.application.service.CostService;
import com.bipros.project.application.service.DprActualCostLookup;
import com.bipros.reporting.application.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Aggregates raw domain data into the six "standard" report shapes the frontend
 * reports page consumes. All queries tolerate missing tables/rows and return
 * empty-but-well-formed payloads rather than throwing — the reports endpoints
 * must stay green (HTTP 200) even on projects that don't have every data source
 * populated.
 *
 * <p>Key data sources:
 * <ul>
 *   <li>{@code public.monthly_evm_snapshots} — canonical EVM source (IC-PMS Phase E
 *       seeds 9 monthly rows for DMIC-PROG). {@code evm.evm_calculations} is an
 *       entity but never seeded, so we query it only as a secondary fallback.
 *   <li>{@code cost.activity_expenses} — budgeted/actual cost per activity,
 *       seeded for all projects in Phase C.
 *   <li>{@code cost.cash_flow_forecasts} — exists but not seeded; we derive the
 *       cash-flow report from {@code activity_expenses} when the table is empty.
 *   <li>{@code risk.risks} — seeded with RAG band (CRIMSON/RED/AMBER/GREEN/
 *       OPPORTUNITY); no {@code severity} column exists, so we band by RAG.
 *   <li>{@code resource.equipment_logs} — daily op/idle/breakdown hours per
 *       equipment, seeded 14 days ending 2026-04-14.
 *   <li>{@code contract.contracts} — seeded in Phase B with denormalised
 *       {@code spi/cpi/bg_expiry/physical_progress_ai} columns.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDataService {

  @PersistenceContext private EntityManager em;

  private final CostService costService;
  private final DprActualCostLookup dprActualCostLookup;

  // =========================================================================
  // 1. Monthly Progress
  // =========================================================================
  @Transactional(readOnly = true)
  public MonthlyProgressData getMonthlyProgress(UUID projectId, String period) {
    String projectName = getProjectName(projectId);
    String projectCode = getProjectCode(projectId);

    YearMonth ym;
    try {
      ym = YearMonth.parse(period);
    } catch (Exception e) {
      ym = YearMonth.now();
    }
    LocalDate monthStart = ym.atDay(1);
    LocalDate monthEnd = ym.atEndOfMonth();

    CostSummaryDto cs = costService.getCostSummary(projectId);

    int totalActivities = scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM activity.activities WHERE project_id = ?1",
        projectId);
    int completedActivities = countCompletedActivities(projectId, monthEnd);
    int inProgressActivities = countInProgressActivities(projectId);
    double overallPercentComplete = cs.costPercentComplete() != null ? cs.costPercentComplete().doubleValue() * 100 : 0.0;

    BigDecimal budgetAmount = getProjectBudget(projectId);
    BigDecimal actualCost = nz(cs.totalActual());
    BigDecimal forecastCost = nz(cs.estimateAtCompletion());

    int totalMilestones = getTotalMilestones(projectId);
    int achievedMilestones = getAchievedMilestones(projectId, monthEnd);

    int openRisks = getOpenRisks(projectId);
    int highRisks = getHighRisks(projectId);

    List<MonthlyProgressData.ActivitySummaryRow> topDelayed =
        getTopDelayedActivities(projectId, 5);

    return new MonthlyProgressData(
        projectName, projectCode, period,
        totalActivities, completedActivities, inProgressActivities, overallPercentComplete,
        budgetAmount, actualCost, forecastCost,
        totalMilestones, achievedMilestones,
        openRisks, highRisks,
        topDelayed);
  }

  // =========================================================================
  // 2. EVM
  // =========================================================================
  @Transactional(readOnly = true)
  public EvmReportData getEvmReport(UUID projectId) {
    String projectName = getProjectName(projectId);

    CostSummaryDto cs = costService.getCostSummary(projectId);
    BigDecimal pv = nz(cs.plannedValue());
    BigDecimal ev = nz(cs.earnedValue());
    BigDecimal ac = nz(cs.totalActual());
    BigDecimal bac = nz(cs.bac());
    double spi = dbl(cs.schedulePerformanceIndex());
    double cpi = dbl(cs.costPerformanceIndex());
    BigDecimal eac = nz(cs.estimateAtCompletion());
    BigDecimal etc = nz(cs.estimateToComplete());
    BigDecimal vac = nz(cs.varianceAtCompletion());
    double tcpi = dbl(cs.toCompletePerformanceIndex());

    return new EvmReportData(projectName, pv, ev, ac, bac, spi, cpi, eac, etc, vac, tcpi);
  }

  // =========================================================================
  // 3. Cash Flow
  // =========================================================================
  @Transactional(readOnly = true)
  public List<CashFlowEntry> getCashFlowReport(UUID projectId) {
    // Primary source: cost.cash_flow_forecasts. The table exists but isn't
    // seeded, so this usually returns empty — we then fall back to an
    // activity_expenses monthly rollup.
    List<CashFlowEntry> forecasted = queryCashFlowForecasts(projectId);
    if (!forecasted.isEmpty()) {
      return forecasted;
    }
    List<CashFlowEntry> fromActivities = deriveCashFlowFromActivities(projectId);
    if (!fromActivities.isEmpty()) {
      return fromActivities;
    }
    return deriveCashFlowFromDpr(projectId);
  }

  // =========================================================================
  // 4. Contract Status
  // =========================================================================
  @Transactional(readOnly = true)
  public ContractStatusData getContractStatus(UUID projectId) {
    String projectName = getProjectName(projectId);
    int totalContracts = getTotalContracts(projectId);
    int activeContracts = getActiveContracts(projectId);
    BigDecimal totalContractValue = getTotalContractValue(projectId);
    BigDecimal totalVoValue = getTotalVariationOrderValue(projectId);
    int pendingMilestones = getPendingMilestones(projectId);
    int achievedMilestones = getAchievedMilestones(projectId);
    List<ContractStatusData.ContractSummaryRow> contracts = getContractSummaries(projectId);

    return new ContractStatusData(
        projectName,
        totalContracts,
        activeContracts,
        totalContractValue,
        totalVoValue,
        pendingMilestones,
        achievedMilestones,
        contracts);
  }

  // =========================================================================
  // 5. Risk Register
  // =========================================================================
  @Transactional(readOnly = true)
  public RiskRegisterData getRiskRegister(UUID projectId) {
    String projectName = getProjectName(projectId);
    int totalRisks = getTotalRisks(projectId);
    // risk.risks has no `severity` column — it has `rag` (CRIMSON/RED/AMBER/GREEN/
    // OPPORTUNITY). Band accordingly: HIGH = CRIMSON+RED, MEDIUM = AMBER, LOW = GREEN.
    int highRisks = getRisksByRag(projectId, List.of("CRIMSON", "RED"));
    int mediumRisks = getRisksByRag(projectId, List.of("AMBER"));
    int lowRisks = getRisksByRag(projectId, List.of("GREEN"));
    Map<String, Integer> risksByCategory = getRisksByCategory(projectId);
    List<RiskRegisterData.RiskSummaryRow> topRisks = getTopRisks(projectId, 10);

    return new RiskRegisterData(
        projectName,
        totalRisks,
        highRisks, mediumRisks, lowRisks,
        risksByCategory,
        topRisks);
  }

  // =========================================================================
  // 6. Resource Utilisation
  // =========================================================================
  @Transactional(readOnly = true)
  public ResourceUtilizationData getResourceUtilization(UUID projectId) {
    String projectName = getProjectName(projectId);

    // Primary source: equipment_logs rolled up per resource. If the project
    // has no logged equipment, fall back to resource.resource_assignments
    // planned vs actual units (used by the legacy reports page).
    List<ResourceUtilizationData.ResourceUtilRow> resources =
        getResourceUtilizationFromEquipmentLogs(projectId);
    if (resources.isEmpty()) {
      resources = getResourceUtilizationFromAssignments(projectId);
    }

    int totalResources = resources.size();
    double avgUtilization = resources.isEmpty()
        ? 0.0
        : resources.stream()
            .mapToDouble(ResourceUtilizationData.ResourceUtilRow::utilPct)
            .average()
            .orElse(0.0);

    return new ResourceUtilizationData(
        projectName,
        totalResources,
        avgUtilization,
        resources);
  }

  // =========================================================================
  // Trend Analysis (kept as-is from prior implementation)
  // =========================================================================
  @Transactional(readOnly = true)
  public TrendAnalysisData getTrendAnalysis(UUID projectId, int months) {
    String projectName = getProjectName(projectId);
    List<TrendAnalysisData.PeriodMetric> periodMetrics = getPeriodMetrics(projectId, months);
    List<TrendAnalysisData.MilestoneStatusRow> milestoneStatus = getMilestoneStatus(projectId);
    Map<String, Integer> activityDistribution = getActivityDistribution(projectId);
    List<TrendAnalysisData.ResourceLoadingEntry> resourceLoadingTrend =
        getResourceLoadingTrend(projectId, months);

    return new TrendAnalysisData(
        projectName,
        periodMetrics,
        milestoneStatus,
        activityDistribution,
        resourceLoadingTrend);
  }

  // =========================================================================
  // EVM helpers
  // =========================================================================
  private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
  private static double dbl(BigDecimal v) { return v != null ? v.doubleValue() : 0.0; }

  /** Compact value object for the latest EVM snapshot. */
  private record EvmSnapshot(
      BigDecimal bcws, BigDecimal bcwp, BigDecimal acwp, BigDecimal bac) {}

  private EvmSnapshot loadLatestEvmSnapshot(UUID projectId) {
    // Sum BCWS/BCWP/ACWP/BAC across all node rows for the most recent report_month.
    try {
      Object[] row = (Object[]) em.createNativeQuery(
              "SELECT " +
              "  COALESCE(SUM(bcws), 0), COALESCE(SUM(bcwp), 0), " +
              "  COALESCE(SUM(acwp), 0), COALESCE(SUM(bac), 0) " +
              "FROM public.monthly_evm_snapshots " +
              "WHERE project_id = ?1 " +
              "  AND report_month = (" +
              "    SELECT MAX(report_month) FROM public.monthly_evm_snapshots " +
              "    WHERE project_id = ?1)")
          .setParameter(1, projectId)
          .getSingleResult();
      return new EvmSnapshot(toBigDecimal(row[0]), toBigDecimal(row[1]),
          toBigDecimal(row[2]), toBigDecimal(row[3]));
    } catch (Exception e) {
      log.debug("No monthly_evm_snapshots for projectId={}: {}", projectId, e.getMessage());
      return new EvmSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
  }

  // =========================================================================
  // Cash-flow helpers
  // =========================================================================
  private List<CashFlowEntry> queryCashFlowForecasts(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "SELECT period, planned_amount, actual_amount, forecast_amount, " +
              "  cumulative_planned, cumulative_actual, cumulative_forecast " +
              "FROM cost.cash_flow_forecasts " +
              "WHERE project_id = ?1 ORDER BY period ASC")
          .setParameter(1, projectId)
          .getResultList();

      return rows.stream().map(r -> {
        Object[] c = (Object[]) r;
        return new CashFlowEntry(
            c[0] != null ? c[0].toString() : "",
            toBigDecimal(c[1]), toBigDecimal(c[2]), toBigDecimal(c[3]),
            toBigDecimal(c[4]), toBigDecimal(c[5]), toBigDecimal(c[6]));
      }).collect(Collectors.toList());
    } catch (Exception e) {
      log.debug("cash_flow_forecasts query failed for projectId={}: {}",
          projectId, e.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * Derive cash-flow rows from {@code activity_expenses} when no forecast table
   * is populated. "Planned" per month = sum of budgeted_cost whose
   * planned_start_date falls in that month; "Actual" = sum of actual_cost whose
   * actual_start_date falls in that month.
   */
  private List<CashFlowEntry> deriveCashFlowFromActivities(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "SELECT " +
              "  to_char(COALESCE(planned_start_date, actual_start_date), 'YYYY-MM') AS period, " +
              "  COALESCE(SUM(CASE WHEN planned_start_date IS NOT NULL THEN budgeted_cost ELSE 0 END), 0) AS planned, " +
              "  COALESCE(SUM(CASE WHEN actual_start_date IS NOT NULL THEN actual_cost ELSE 0 END), 0) AS actual, " +
              "  COALESCE(SUM(at_completion_cost), 0) AS forecast " +
              "FROM cost.activity_expenses " +
              "WHERE project_id = ?1 " +
              "  AND COALESCE(planned_start_date, actual_start_date) IS NOT NULL " +
              // Postgres allows GROUP BY on an output column-alias at the top level; safer
              // to repeat the expression so this also works on other dialects.
              "GROUP BY to_char(COALESCE(planned_start_date, actual_start_date), 'YYYY-MM') " +
              "ORDER BY to_char(COALESCE(planned_start_date, actual_start_date), 'YYYY-MM') ASC")
          .setParameter(1, projectId)
          .getResultList();

      // Build running cumulative totals.
      BigDecimal cumPlanned = BigDecimal.ZERO;
      BigDecimal cumActual = BigDecimal.ZERO;
      BigDecimal cumForecast = BigDecimal.ZERO;
      List<CashFlowEntry> result = new ArrayList<>(rows.size());
      for (Object r : rows) {
        Object[] c = (Object[]) r;
        BigDecimal planned = toBigDecimal(c[1]);
        BigDecimal actual = toBigDecimal(c[2]);
        BigDecimal forecast = toBigDecimal(c[3]);
        cumPlanned = cumPlanned.add(planned);
        cumActual = cumActual.add(actual);
        cumForecast = cumForecast.add(forecast);
        result.add(new CashFlowEntry(
            c[0] != null ? c[0].toString() : "",
            planned, actual, forecast,
            cumPlanned, cumActual, cumForecast));
      }
      return result;
    } catch (Exception e) {
      log.warn("Failed to derive cash flow from activity_expenses for projectId={}: {}",
          projectId, e.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * Third cash-flow fallback: build a monthly series from the DPR ledger (actual)
   * and resource_assignments planned_cost (planned/forecast) when both primary
   * sources are empty. Degrades gracefully — on any error returns empty list.
   */
  private List<CashFlowEntry> deriveCashFlowFromDpr(UUID projectId) {
    try {
      // ACTUAL: bucket DPR daily costs into YYYY-MM.
      Map<String, BigDecimal> actualByMonth = new TreeMap<>();
      Map<LocalDate, BigDecimal> dailyActuals = dprActualCostLookup.sumByProjectGroupedByDate(projectId);
      for (Map.Entry<LocalDate, BigDecimal> e : dailyActuals.entrySet()) {
        String month = e.getKey().toString().substring(0, 7); // YYYY-MM
        actualByMonth.merge(month, e.getValue(), BigDecimal::add);
      }

      // PLANNED: SUM(planned_cost) by activity planned_start_date month.
      Map<String, BigDecimal> plannedByMonth = new TreeMap<>();
      try {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(
                "SELECT to_char(a.planned_start_date, 'YYYY-MM') AS m, " +
                "  COALESCE(SUM(ra.planned_cost), 0) " +
                "FROM resource.resource_assignments ra " +
                "JOIN activity.activities a ON a.id = ra.activity_id " +
                "WHERE ra.project_id = ?1 AND a.planned_start_date IS NOT NULL " +
                "GROUP BY to_char(a.planned_start_date, 'YYYY-MM')")
            .setParameter(1, projectId)
            .getResultList();
        for (Object r : rows) {
          Object[] c = (Object[]) r;
          if (c[0] != null) {
            plannedByMonth.put(c[0].toString(), toBigDecimal(c[1]));
          }
        }
      } catch (Exception e) {
        log.debug("DPR cash-flow planned query failed for projectId={}: {}", projectId, e.getMessage());
        // Proceed with actual-only — still non-empty if actualByMonth has data.
      }

      // Merge all months.
      Set<String> allMonths = new TreeSet<>();
      allMonths.addAll(actualByMonth.keySet());
      allMonths.addAll(plannedByMonth.keySet());
      if (allMonths.isEmpty()) return new ArrayList<>();

      // Fetch canonical EVM snapshot once for BAC (planned scale anchor) and EAC (forecast target).
      CostSummaryDto cs = costService.getCostSummary(projectId);
      BigDecimal bac = nz(cs.bac());
      BigDecimal eac = nz(cs.estimateAtCompletion());

      return buildCashFlowSeries(allMonths, plannedByMonth, actualByMonth, bac, eac);
    } catch (Exception e) {
      log.warn("Failed to derive cash flow from DPR for projectId={}: {}",
          projectId, e.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * Pure helper: builds a monthly cash-flow series from raw planned/actual maps.
   *
   * <ul>
   *   <li>Planned is scaled so its total equals {@code bac} (budget-anchored).</li>
   *   <li>Forecast = actual for months that have actual data; for remaining months
   *       the ETC ({@code eac − Σactual}) is distributed in proportion to the
   *       scaled-planned shape. Cumulative forecast is always ≥ cumulative actual.</li>
   * </ul>
   *
   * Package-private so {@code CashFlowForecastTest} can call it directly.
   */
  static List<CashFlowEntry> buildCashFlowSeries(
      Set<String> allMonths,
      Map<String, BigDecimal> plannedByMonth,
      Map<String, BigDecimal> actualByMonth,
      BigDecimal bac,
      BigDecimal eac) {

    // (a) Scale planned to BAC. Use high-precision division (10dp) so that
    //     the per-month values sum back to BAC within rounding noise < 1 unit.
    BigDecimal plannedTotal = BigDecimal.ZERO;
    for (String m : allMonths) plannedTotal = plannedTotal.add(
        plannedByMonth.getOrDefault(m, BigDecimal.ZERO));
    BigDecimal plannedScale = (bac.signum() > 0 && plannedTotal.signum() > 0)
        ? bac.divide(plannedTotal, 10, RoundingMode.HALF_UP) : BigDecimal.ONE;

    // Keep full intermediate precision; rounding happens in the emit loop.
    Map<String, BigDecimal> scaledPlanned = new LinkedHashMap<>();
    for (String m : allMonths) {
      scaledPlanned.put(m, plannedByMonth.getOrDefault(m, BigDecimal.ZERO)
          .multiply(plannedScale));
    }

    // (b) Compute ETC = EAC − Σactual; sum remaining scaled-planned weight
    //     (months where actual == 0) for proportional distribution.
    BigDecimal totalActual = BigDecimal.ZERO;
    for (String m : allMonths) totalActual = totalActual.add(
        actualByMonth.getOrDefault(m, BigDecimal.ZERO));
    BigDecimal etc = eac.subtract(totalActual);
    if (etc.signum() < 0) etc = BigDecimal.ZERO;

    BigDecimal remainingWeight = BigDecimal.ZERO;
    for (String m : allMonths) {
      if (actualByMonth.getOrDefault(m, BigDecimal.ZERO).signum() == 0) {
        remainingWeight = remainingWeight.add(scaledPlanned.getOrDefault(m, BigDecimal.ZERO));
      }
    }

    // Emit loop.
    BigDecimal cumPlanned = BigDecimal.ZERO;
    BigDecimal cumActual = BigDecimal.ZERO;
    BigDecimal cumForecast = BigDecimal.ZERO;
    String lastMonth = null;
    List<CashFlowEntry> result = new ArrayList<>(allMonths.size());
    for (String month : allMonths) {
      BigDecimal sp = scaledPlanned.getOrDefault(month, BigDecimal.ZERO);
      BigDecimal actual = actualByMonth.getOrDefault(month, BigDecimal.ZERO);
      BigDecimal forecastMonth;
      if (actual.signum() > 0) {
        // Past month with real expenditure: forecast tracks actual.
        forecastMonth = actual;
      } else {
        // Future month: distribute remaining ETC by scaled-planned weight.
        // Scale 4 matches plannedScale precision; keeps long-horizon cumulative drift minimal.
        forecastMonth = remainingWeight.signum() > 0
            ? etc.multiply(sp).divide(remainingWeight, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
      }
      cumPlanned = cumPlanned.add(sp);
      cumActual = cumActual.add(actual);
      BigDecimal prevCumForecast = cumForecast;
      cumForecast = cumForecast.add(forecastMonth);
      // Defensive clamp: cumForecast >= cumActual invariant already holds by
      // construction (past months: forecastMonth==actual; future: ETC >= 0).
      if (cumForecast.compareTo(cumActual) < 0) cumForecast = cumActual;
      BigDecimal emittedMonthly = cumForecast.subtract(prevCumForecast);
      result.add(new CashFlowEntry(month, sp, actual, emittedMonthly,
          cumPlanned, cumActual, cumForecast));
      lastMonth = month;
    }

    // Trailing-period fix: when all months are past (remainingWeight == 0) and ETC > 0,
    // the loop above sets cumForecast == Σactual < eac. Append a synthetic trailing period
    // so that cumulative forecast reaches EAC — matching the EVM card figure.
    if (etc.signum() > 0 && remainingWeight.signum() == 0 && cumForecast.compareTo(eac) < 0) {
      BigDecimal etcRemainder = eac.subtract(cumForecast);
      String trailingMonth = lastMonth != null
          ? YearMonth.parse(lastMonth).plusMonths(1).toString()
          : "9999-01";
      result.add(new CashFlowEntry(
          trailingMonth, BigDecimal.ZERO, BigDecimal.ZERO, etcRemainder,
          cumPlanned, cumActual, eac));
    }

    return result;
  }

  // =========================================================================
  // Resource helpers
  // =========================================================================
  private List<ResourceUtilizationData.ResourceUtilRow> getResourceUtilizationFromEquipmentLogs(
      UUID projectId) {
    try {
      // utilization = operatingHours / (operatingHours + idleHours + breakdownHours) * 100.
      // operatingHours doubles as "actualHours"; total of all three is the exposed time.
      // Each hour component can be NULL — coalesce per-row before summing so one NULL
      // doesn't null out the whole group.
      // r.resource_type became r.resource_type_id (FK) after the resource rewrite —
      // join through resource_types to surface the type code (EQUIPMENT / LABOR / MATERIAL).
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "SELECT r.code, r.name, COALESCE(rt.code, ''), " +
              "  SUM(COALESCE(l.operating_hours, 0) + COALESCE(l.idle_hours, 0) + COALESCE(l.breakdown_hours, 0)) AS planned, " +
              "  SUM(COALESCE(l.operating_hours, 0)) AS actual, " +
              "  CASE WHEN SUM(COALESCE(l.operating_hours, 0) + COALESCE(l.idle_hours, 0) + COALESCE(l.breakdown_hours, 0)) > 0 " +
              "    THEN ROUND((100.0 * SUM(COALESCE(l.operating_hours, 0)) / " +
              "      SUM(COALESCE(l.operating_hours, 0) + COALESCE(l.idle_hours, 0) + COALESCE(l.breakdown_hours, 0)))::numeric, 2) " +
              "    ELSE 0 END AS util_pct " +
              "FROM resource.equipment_logs l " +
              "JOIN resource.resources r ON l.resource_id = r.id " +
              "LEFT JOIN resource.resource_types rt ON rt.id = r.resource_type_id " +
              "WHERE l.project_id = ?1 " +
              "GROUP BY r.id, r.code, r.name, rt.code " +
              "ORDER BY util_pct DESC")
          .setParameter(1, projectId)
          .getResultList();

      return rows.stream().map(r -> {
        Object[] c = (Object[]) r;
        return new ResourceUtilizationData.ResourceUtilRow(
            c[0] != null ? c[0].toString() : "",
            c[1] != null ? c[1].toString() : "",
            c[2] != null ? c[2].toString() : "",
            toDouble(c[3]), toDouble(c[4]), toDouble(c[5]));
      }).collect(Collectors.toList());
    } catch (Exception e) {
      log.debug("equipment_logs roll-up failed for projectId={}: {}", projectId, e.getMessage());
      return new ArrayList<>();
    }
  }

  private List<ResourceUtilizationData.ResourceUtilRow> getResourceUtilizationFromAssignments(
      UUID projectId) {
    try {
      // Union over legacy (resource_id chain) and role-only (role_id + variant chain). Type code
      // is derived from the variant FK when the legacy resource join misses.
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "WITH legacy AS (" +
              "  SELECT r.code AS code, r.name AS name, COALESCE(rt.code, '') AS type_code, " +
              "         COALESCE(SUM(a.planned_units), 0) AS planned_units, " +
              "         COALESCE(SUM(a.actual_units), 0)  AS actual_units " +
              "  FROM resource.resources r " +
              "  JOIN resource.resource_assignments a ON r.id = a.resource_id " +
              "  LEFT JOIN resource.resource_types rt ON rt.id = r.resource_type_id " +
              "  WHERE a.project_id = ?1 AND a.resource_id IS NOT NULL " +
              "  GROUP BY r.id, r.code, r.name, rt.code " +
              "), role_only AS (" +
              // Put planned and actual on the SAME cumulative basis so the ratio is meaningful.
              // MANPOWER: planned = Σ(headcount × duration) man-days; actual = Σ DPR man-days.
              // EQUIPMENT: planned_units already stores cumulative hours per assignment row;
              //   actual = Σ DPR hours. Using headcount for equipment is wrong (it is sometimes
              //   set to an hours-per-day figure, not a count), so use planned_units instead.
              // MATERIAL: excluded from the final result — no man-day/headcount basis applies.
              "  SELECT COALESCE(rr.code, rr.name) AS code, rr.name AS name, " +
              "         CASE WHEN a.manpower_role_rate_id  IS NOT NULL THEN 'MANPOWER' " +
              "              WHEN a.equipment_role_variant_id IS NOT NULL THEN 'EQUIPMENT' " +
              "              WHEN a.material_role_variant_id  IS NOT NULL THEN 'MATERIAL' " +
              "              ELSE '' END AS type_code, " +
              "         CASE WHEN a.manpower_role_rate_id IS NOT NULL " +
              "              THEN COALESCE(SUM(COALESCE(a.headcount, 0) * COALESCE(a.duration, 0.0)), 0.0) " +
              "              ELSE COALESCE(SUM(a.planned_units), 0.0) " +
              "         END AS planned_units, " +
              "         COALESCE(SUM(a.actual_units), 0) AS actual_units " +
              "  FROM resource.resource_assignments a " +
              "  JOIN resource.resource_roles rr ON rr.id = a.role_id " +
              "  WHERE a.project_id = ?1 AND a.resource_id IS NULL AND a.role_id IS NOT NULL " +
              "  GROUP BY rr.id, rr.code, rr.name, " +
              "           a.manpower_role_rate_id, a.equipment_role_variant_id, a.material_role_variant_id " +
              ") " +
              "SELECT code, name, type_code, planned_units, actual_units, " +
              "  CASE WHEN planned_units > 0 " +
              "    THEN ROUND((100.0 * actual_units / planned_units)::numeric, 2) ELSE 0 END AS util_pct " +
              "FROM (SELECT * FROM legacy UNION ALL SELECT * FROM role_only) u " +
              "WHERE u.type_code != 'MATERIAL' " +
              "ORDER BY util_pct DESC")
          .setParameter(1, projectId)
          .getResultList();

      return rows.stream().map(r -> {
        Object[] c = (Object[]) r;
        return new ResourceUtilizationData.ResourceUtilRow(
            c[0] != null ? c[0].toString() : "",
            c[1] != null ? c[1].toString() : "",
            c[2] != null ? c[2].toString() : "",
            toDouble(c[3]), toDouble(c[4]), toDouble(c[5]));
      }).collect(Collectors.toList());
    } catch (Exception e) {
      log.debug("resource_assignments roll-up failed for projectId={}: {}",
          projectId, e.getMessage());
      return new ArrayList<>();
    }
  }

  // =========================================================================
  // Activity + WBS helpers
  // =========================================================================
  private int countActivitiesInWindow(UUID projectId, LocalDate start, LocalDate end) {
    // Activities whose planned window overlaps the report month.
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM activity.activities " +
        "WHERE project_id = ?1 " +
        "  AND COALESCE(planned_start_date, actual_start_date) <= ?3 " +
        "  AND COALESCE(planned_finish_date, actual_finish_date, planned_start_date) >= ?2",
        projectId, start, end);
  }

  private int countCompletedActivities(UUID projectId, LocalDate asOf) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM activity.activities " +
        "WHERE project_id = ?1 AND status = 'COMPLETED' " +
        "  AND (actual_finish_date IS NULL OR actual_finish_date <= ?2)",
        projectId, asOf);
  }

  private int countInProgressActivities(UUID projectId) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM activity.activities " +
        "WHERE project_id = ?1 AND status = 'IN_PROGRESS'",
        projectId);
  }

  private List<MonthlyProgressData.ActivitySummaryRow> getTopDelayedActivities(
      UUID projectId, int limit) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "SELECT code, name, status, " +
              "  GREATEST(0, (COALESCE((SELECT p.data_date FROM project.projects p WHERE p.id = ?1), CURRENT_DATE) - planned_finish_date)) AS delay_days, " +
              "  planned_finish_date " +
              "FROM activity.activities " +
              "WHERE project_id = ?1 " +
              "  AND actual_finish_date IS NULL " +
              "  AND planned_finish_date IS NOT NULL " +
              "  AND planned_finish_date < COALESCE((SELECT p.data_date FROM project.projects p WHERE p.id = ?1), CURRENT_DATE) " +
              "ORDER BY delay_days DESC, planned_finish_date ASC " +
              "LIMIT ?2")
          .setParameter(1, projectId)
          .setParameter(2, limit)
          .getResultList();

      return rows.stream().map(r -> {
        Object[] c = (Object[]) r;
        LocalDate plannedFinish = null;
        if (c[4] != null) {
          if (c[4] instanceof LocalDate ld) plannedFinish = ld;
          else if (c[4] instanceof java.sql.Date sd) plannedFinish = sd.toLocalDate();
          else try { plannedFinish = LocalDate.parse(c[4].toString()); } catch (Exception ignored) {}
        }
        return new MonthlyProgressData.ActivitySummaryRow(
            c[0] != null ? c[0].toString() : "",
            c[1] != null ? c[1].toString() : "",
            c[2] != null ? c[2].toString() : "",
            toInt(c[3]),
            plannedFinish);
      }).collect(Collectors.toList());
    } catch (Exception e) {
      log.debug("top delayed query failed for projectId={}: {}", projectId, e.getMessage());
      return new ArrayList<>();
    }
  }

  // =========================================================================
  // Cost helpers
  // =========================================================================
  private BigDecimal getProjectBudget(UUID projectId) {
    // Project.current_budget is stored in the currency's major-unit
    // (crore = 1e7 for INR, million = 1e6 for every other currency).
    // Multiply back to raw money before returning.
    try {
      Object[] row = (Object[]) em.createNativeQuery(
              "SELECT COALESCE(current_budget, 0), COALESCE(budget_currency, 'INR') " +
              "FROM project.projects WHERE id = ?1")
          .setParameter(1, projectId)
          .getSingleResult();
      BigDecimal rawBudget = toBigDecimal(row[0]);
      if (rawBudget.signum() > 0) {
        String currencyCode = row[1] != null ? row[1].toString() : "INR";
        BigDecimal factor = "INR".equalsIgnoreCase(currencyCode)
            ? new BigDecimal("10000000") : new BigDecimal("1000000");
        return rawBudget.multiply(factor);
      }
    } catch (Exception e) {
      log.debug("getProjectBudget project row failed for projectId={}: {}", projectId, e.getMessage());
    }

    return scalarDecimal(
        "SELECT COALESCE(SUM(budgeted_cost), 0) FROM cost.activity_expenses WHERE project_id = ?1",
        projectId);
  }

  private BigDecimal getActualCostInWindow(UUID projectId, LocalDate start, LocalDate end) {
    return scalarDecimal(
        "SELECT COALESCE(SUM(actual_cost), 0) FROM cost.activity_expenses " +
        "WHERE project_id = ?1 " +
        "  AND COALESCE(actual_start_date, planned_start_date) BETWEEN ?2 AND ?3",
        projectId, start, end);
  }

  private BigDecimal getForecastCost(UUID projectId) {
    return scalarDecimal(
        "SELECT COALESCE(SUM(at_completion_cost), 0) FROM cost.activity_expenses " +
        "WHERE project_id = ?1",
        projectId);
  }

  // =========================================================================
  // Milestone / Contract helpers
  // =========================================================================
  private int getTotalMilestones(UUID projectId) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM contract.contract_milestones cm " +
        "JOIN contract.contracts c ON cm.contract_id = c.id WHERE c.project_id = ?1",
        projectId);
  }

  private int getAchievedMilestones(UUID projectId, LocalDate asOf) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM contract.contract_milestones cm " +
        "JOIN contract.contracts c ON cm.contract_id = c.id " +
        "WHERE c.project_id = ?1 AND cm.actual_date IS NOT NULL AND cm.actual_date <= ?2",
        projectId, asOf);
  }

  private int getAchievedMilestones(UUID projectId) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM contract.contract_milestones cm " +
        "JOIN contract.contracts c ON cm.contract_id = c.id " +
        "WHERE c.project_id = ?1 AND cm.actual_date IS NOT NULL",
        projectId);
  }

  private int getPendingMilestones(UUID projectId) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM contract.contract_milestones cm " +
        "JOIN contract.contracts c ON cm.contract_id = c.id " +
        "WHERE c.project_id = ?1 AND cm.actual_date IS NULL",
        projectId);
  }

  private int getTotalContracts(UUID projectId) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM contract.contracts WHERE project_id = ?1",
        projectId);
  }

  private int getActiveContracts(UUID projectId) {
    // "Active" covers every ACTIVE_* band plus MOBILISATION and DELAYED (still
    // under execution). Terminal states: COMPLETED, TERMINATED, DLP, DRAFT, SUSPENDED.
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM contract.contracts " +
        "WHERE project_id = ?1 AND status IN ('ACTIVE', 'ACTIVE_AT_RISK', 'ACTIVE_DELAYED', 'MOBILISATION', 'DELAYED')",
        projectId);
  }

  private BigDecimal getTotalContractValue(UUID projectId) {
    return scalarDecimal(
        "SELECT COALESCE(SUM(contract_value), 0) FROM contract.contracts WHERE project_id = ?1",
        projectId);
  }

  private BigDecimal getTotalVariationOrderValue(UUID projectId) {
    return scalarDecimal(
        "SELECT COALESCE(SUM(vo.vo_value), 0) FROM contract.variation_orders vo " +
        "JOIN contract.contracts c ON vo.contract_id = c.id WHERE c.project_id = ?1",
        projectId);
  }

  private List<ContractStatusData.ContractSummaryRow> getContractSummaries(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "SELECT c.contract_number, c.contractor_name, " +
              "  COALESCE(c.contract_value, 0), COALESCE(c.status::text, ''), " +
              "  (SELECT COUNT(*) FROM contract.contract_milestones cm " +
              "   WHERE cm.contract_id = c.id AND cm.actual_date IS NULL) AS pending_count " +
              "FROM contract.contracts c " +
              "WHERE c.project_id = ?1 " +
              "ORDER BY c.contract_value DESC NULLS LAST " +
              "LIMIT 20")
          .setParameter(1, projectId)
          .getResultList();

      return rows.stream().map(r -> {
        Object[] c = (Object[]) r;
        return new ContractStatusData.ContractSummaryRow(
            c[0] != null ? c[0].toString() : "",
            c[1] != null ? c[1].toString() : "",
            toBigDecimal(c[2]),
            c[3] != null ? c[3].toString() : "",
            toInt(c[4]));
      }).collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Failed to get contract summaries for projectId={}: {}", projectId, e.getMessage());
      return new ArrayList<>();
    }
  }

  // =========================================================================
  // Risk helpers
  // =========================================================================
  private int getOpenRisks(UUID projectId) {
    // "Open" == anything not in a terminal state. RiskStatus enum terminal states:
    // CLOSED, RESOLVED, ACCEPTED. Every OPEN_* sub-variant (and IDENTIFIED, ANALYZING,
    // MITIGATING, REALISED_PARTIALLY) counts as open.
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM risk.risks " +
        "WHERE project_id = ?1 AND status NOT IN ('CLOSED', 'RESOLVED', 'ACCEPTED')",
        projectId);
  }

  private int getHighRisks(UUID projectId) {
    // RAG-based: CRIMSON or RED counts as high.
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM risk.risks " +
        "WHERE project_id = ?1 AND rag IN ('CRIMSON', 'RED')",
        projectId);
  }

  private int getTotalRisks(UUID projectId) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM risk.risks WHERE project_id = ?1",
        projectId);
  }

  private int getRisksByRag(UUID projectId, List<String> rags) {
    if (rags.isEmpty()) return 0;
    StringBuilder sb = new StringBuilder(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM risk.risks WHERE project_id = ?1 AND rag IN (");
    for (int i = 0; i < rags.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append("?").append(i + 2);
    }
    sb.append(")");
    try {
      var query = em.createNativeQuery(sb.toString()).setParameter(1, projectId);
      for (int i = 0; i < rags.size(); i++) {
        query.setParameter(i + 2, rags.get(i));
      }
      Object result = query.getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      log.debug("risks-by-rag query failed: {}", e.getMessage());
      return 0;
    }
  }

  private Map<String, Integer> getRisksByCategory(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "SELECT COALESCE(rcm.name, 'UNKNOWN'), CAST(COUNT(*) AS INTEGER) " +
              "FROM risk.risks r " +
              "LEFT JOIN risk.risk_category_master rcm ON rcm.id = r.category_id " +
              "WHERE r.project_id = ?1 " +
              "GROUP BY rcm.name")
          .setParameter(1, projectId)
          .getResultList();

      Map<String, Integer> out = new HashMap<>();
      for (Object r : rows) {
        Object[] c = (Object[]) r;
        out.put(c[0] != null ? c[0].toString() : "UNKNOWN", toInt(c[1]));
      }
      return out;
    } catch (Exception e) {
      log.debug("risks-by-category query failed: {}", e.getMessage());
      return new HashMap<>();
    }
  }

  private List<RiskRegisterData.RiskSummaryRow> getTopRisks(UUID projectId, int limit) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "SELECT r.code, r.title, COALESCE(rcm.name, ''), " +
              "  COALESCE(r.probability::text, ''), " +
              "  COALESCE(r.rag::text, '') AS rag, " +
              "  COALESCE(r.risk_score, 0) " +
              "FROM risk.risks r " +
              "LEFT JOIN risk.risk_category_master rcm ON rcm.id = r.category_id " +
              "WHERE r.project_id = ?1 " +
              "ORDER BY r.risk_score DESC NULLS LAST, r.created_at DESC " +
              "LIMIT ?2")
          .setParameter(1, projectId)
          .setParameter(2, limit)
          .getResultList();

      return rows.stream().map(r -> {
        Object[] c = (Object[]) r;
        return new RiskRegisterData.RiskSummaryRow(
            c[0] != null ? c[0].toString() : "",
            c[1] != null ? c[1].toString() : "",
            c[2] != null ? c[2].toString() : "",
            c[3] != null ? c[3].toString() : "",
            c[4] != null ? c[4].toString() : "",
            toDouble(c[5]));
      }).collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Failed to get top risks for projectId={}: {}", projectId, e.getMessage());
      return new ArrayList<>();
    }
  }

  // =========================================================================
  // Trend helpers (preserved from prior implementation, column names fixed)
  // =========================================================================
  private List<TrendAnalysisData.PeriodMetric> getPeriodMetrics(UUID projectId, int months) {
    List<TrendAnalysisData.PeriodMetric> metrics = new ArrayList<>();
    LocalDate now = LocalDate.now();

    for (int i = months - 1; i >= 0; i--) {
      YearMonth ym = YearMonth.from(now.minusMonths(i));
      String period = ym.toString();
      LocalDate monthEnd = ym.atEndOfMonth();

      int total = countActivitiesAsOf(projectId, monthEnd);
      int completed = countCompletedAsOf(projectId, monthEnd);
      double pct = total > 0 ? (completed * 100.0) / total : 0.0;
      double spi = getSpiAsOf(projectId, monthEnd);
      double cpi = getCpiAsOf(projectId, monthEnd);

      metrics.add(new TrendAnalysisData.PeriodMetric(period, total, completed, pct, spi, cpi));
    }
    return metrics;
  }

  private int countActivitiesAsOf(UUID projectId, LocalDate asOf) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM activity.activities " +
        "WHERE project_id = ?1 AND planned_start_date <= ?2",
        projectId, asOf);
  }

  private int countCompletedAsOf(UUID projectId, LocalDate asOf) {
    return scalarInt(
        "SELECT CAST(COUNT(*) AS INTEGER) FROM activity.activities " +
        "WHERE project_id = ?1 AND status = 'COMPLETED' AND actual_finish_date <= ?2",
        projectId, asOf);
  }

  private double getSpiAsOf(UUID projectId, LocalDate asOf) {
    // Read the pre-computed SPI from the most recent monthly snapshot on/before asOf.
    try {
      Object result = em.createNativeQuery(
              "SELECT COALESCE(AVG(spi), 0) FROM public.monthly_evm_snapshots " +
              "WHERE project_id = ?1 AND report_month <= ?2 " +
              "  AND report_month = (SELECT MAX(report_month) FROM public.monthly_evm_snapshots " +
              "                      WHERE project_id = ?1 AND report_month <= ?2)")
          .setParameter(1, projectId)
          .setParameter(2, asOf)
          .getSingleResult();
      return result != null ? ((Number) result).doubleValue() : 0.0;
    } catch (Exception e) {
      return 0.0;
    }
  }

  private double getCpiAsOf(UUID projectId, LocalDate asOf) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COALESCE(AVG(cpi), 0) FROM public.monthly_evm_snapshots " +
              "WHERE project_id = ?1 AND report_month <= ?2 " +
              "  AND report_month = (SELECT MAX(report_month) FROM public.monthly_evm_snapshots " +
              "                      WHERE project_id = ?1 AND report_month <= ?2)")
          .setParameter(1, projectId)
          .setParameter(2, asOf)
          .getSingleResult();
      return result != null ? ((Number) result).doubleValue() : 0.0;
    } catch (Exception e) {
      return 0.0;
    }
  }

  private List<TrendAnalysisData.MilestoneStatusRow> getMilestoneStatus(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "SELECT cm.milestone_code, cm.milestone_name, cm.target_date, cm.actual_date, " +
              "  CASE WHEN cm.actual_date IS NOT NULL THEN 'ACHIEVED' " +
              "       WHEN cm.target_date < CURRENT_DATE THEN 'LATE' " +
              "       ELSE 'PENDING' END AS status, " +
              "  COALESCE(EXTRACT(DAY FROM (cm.actual_date - cm.target_date)), " +
              "           EXTRACT(DAY FROM (CURRENT_DATE - cm.target_date))) AS variance_days " +
              "FROM contract.contract_milestones cm " +
              "JOIN contract.contracts c ON cm.contract_id = c.id " +
              "WHERE c.project_id = ?1 " +
              "ORDER BY cm.target_date ASC")
          .setParameter(1, projectId)
          .getResultList();

      return rows.stream().map(r -> {
        Object[] c = (Object[]) r;
        return new TrendAnalysisData.MilestoneStatusRow(
            c[0] != null ? c[0].toString() : "",
            c[1] != null ? c[1].toString() : "",
            c[2] != null ? c[2].toString() : "",
            c[3] != null ? c[3].toString() : "",
            c[4] != null ? c[4].toString() : "PENDING",
            toInt(c[5]));
      }).collect(Collectors.toList());
    } catch (Exception e) {
      log.debug("milestone status query failed: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  private Map<String, Integer> getActivityDistribution(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> rows = em.createNativeQuery(
              "SELECT status, CAST(COUNT(*) AS INTEGER) FROM activity.activities " +
              "WHERE project_id = ?1 GROUP BY status")
          .setParameter(1, projectId)
          .getResultList();

      Map<String, Integer> out = new HashMap<>();
      for (Object r : rows) {
        Object[] c = (Object[]) r;
        out.put(c[0] != null ? c[0].toString() : "UNKNOWN", toInt(c[1]));
      }
      return out;
    } catch (Exception e) {
      return new HashMap<>();
    }
  }

  private List<TrendAnalysisData.ResourceLoadingEntry> getResourceLoadingTrend(
      UUID projectId, int months) {
    List<TrendAnalysisData.ResourceLoadingEntry> entries = new ArrayList<>();
    LocalDate now = LocalDate.now();
    for (int i = months - 1; i >= 0; i--) {
      YearMonth ym = YearMonth.from(now.minusMonths(i));
      LocalDate monthStart = ym.atDay(1);
      LocalDate monthEnd = ym.atEndOfMonth();

      try {
        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT COALESCE(SUM(planned_units), 0), COALESCE(SUM(actual_units), 0), " +
                "  COUNT(DISTINCT resource_id) " +
                "FROM resource.resource_assignments " +
                "WHERE project_id = ?1 AND planned_start_date <= ?3 AND planned_finish_date >= ?2")
            .setParameter(1, projectId)
            .setParameter(2, monthStart)
            .setParameter(3, monthEnd)
            .getSingleResult();

        entries.add(new TrendAnalysisData.ResourceLoadingEntry(
            ym.toString(),
            toDouble(row[0]), toDouble(row[1]), toInt(row[2])));
      } catch (Exception e) {
        entries.add(new TrendAnalysisData.ResourceLoadingEntry(ym.toString(), 0.0, 0.0, 0));
      }
    }
    return entries;
  }

  // =========================================================================
  // Project helpers
  // =========================================================================
  private String getProjectName(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT name FROM project.projects WHERE id = ?1")
          .setParameter(1, projectId)
          .getSingleResult();
      return result != null ? result.toString() : "Unknown Project";
    } catch (Exception e) {
      return "Project " + projectId;
    }
  }

  private String getProjectCode(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT code FROM project.projects WHERE id = ?1")
          .setParameter(1, projectId)
          .getSingleResult();
      return result != null ? result.toString() : "N/A";
    } catch (Exception e) {
      return "N/A";
    }
  }

  // =========================================================================
  // Low-level scalar helpers
  // =========================================================================
  private int scalarInt(String sql, Object... params) {
    try {
      var query = em.createNativeQuery(sql);
      for (int i = 0; i < params.length; i++) {
        query.setParameter(i + 1, params[i]);
      }
      Object result = query.getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      log.debug("scalarInt failed [{}]: {}", sql, e.getMessage());
      return 0;
    }
  }

  private BigDecimal scalarDecimal(String sql, Object... params) {
    try {
      var query = em.createNativeQuery(sql);
      for (int i = 0; i < params.length; i++) {
        query.setParameter(i + 1, params[i]);
      }
      Object result = query.getSingleResult();
      return toBigDecimal(result);
    } catch (Exception e) {
      log.debug("scalarDecimal failed [{}]: {}", sql, e.getMessage());
      return BigDecimal.ZERO;
    }
  }

  private static BigDecimal toBigDecimal(Object o) {
    if (o == null) return BigDecimal.ZERO;
    if (o instanceof BigDecimal bd) return bd;
    if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
    try {
      return new BigDecimal(o.toString());
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private static double toDouble(Object o) {
    if (o == null) return 0.0;
    if (o instanceof Number n) return n.doubleValue();
    try {
      return Double.parseDouble(o.toString());
    } catch (Exception e) {
      return 0.0;
    }
  }

  private static int toInt(Object o) {
    if (o == null) return 0;
    if (o instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(o.toString());
    } catch (Exception e) {
      return 0;
    }
  }
}
