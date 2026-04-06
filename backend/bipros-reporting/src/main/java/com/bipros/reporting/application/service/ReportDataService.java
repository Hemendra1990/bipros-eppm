package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDataService {

  @PersistenceContext private EntityManager em;

  @Transactional(readOnly = true)
  public MonthlyProgressData getMonthlyProgress(UUID projectId, String period) {
    try {
      String projectName = getProjectName(projectId);
      String projectCode = getProjectCode(projectId);

      // Parse period (yyyy-MM)
      YearMonth monthYear = YearMonth.parse(period);
      LocalDate monthStart = monthYear.atDay(1);
      LocalDate monthEnd = monthYear.atEndOfMonth();

      // Query activities
      int totalActivities = getTotalActivities(projectId, monthStart, monthEnd);
      int completedActivities = getCompletedActivities(projectId, monthStart, monthEnd);
      int inProgressActivities = getInProgressActivities(projectId, monthStart, monthEnd);
      double overallPercentComplete = totalActivities > 0
          ? (completedActivities * 100.0) / totalActivities
          : 0.0;

      // Query cost data
      BigDecimal budgetAmount = getProjectBudget(projectId);
      BigDecimal actualCost = getActualCost(projectId, monthStart, monthEnd);
      BigDecimal forecastCost = getForecastCost(projectId, monthStart, monthEnd);

      // Query milestones
      int totalMilestones = getTotalMilestones(projectId);
      int achievedMilestones = getAchievedMilestones(projectId, monthEnd);

      // Query risks
      int openRisks = getOpenRisks(projectId);
      int highRisks = getHighRisks(projectId);

      // Top delayed activities
      List<MonthlyProgressData.ActivitySummaryRow> topDelayed = getTopDelayedActivities(
          projectId, 5, monthEnd);

      return new MonthlyProgressData(
          projectName,
          projectCode,
          period,
          totalActivities,
          completedActivities,
          inProgressActivities,
          overallPercentComplete,
          budgetAmount != null ? budgetAmount : BigDecimal.ZERO,
          actualCost != null ? actualCost : BigDecimal.ZERO,
          forecastCost != null ? forecastCost : BigDecimal.ZERO,
          totalMilestones,
          achievedMilestones,
          openRisks,
          highRisks,
          topDelayed);
    } catch (Exception e) {
      log.error("Error generating monthly progress report for projectId={}, period={}",
          projectId, period, e);
      throw new RuntimeException("Failed to generate monthly progress report", e);
    }
  }

  @Transactional(readOnly = true)
  public EvmReportData getEvmReport(UUID projectId) {
    try {
      String projectName = getProjectName(projectId);

      // Query EVM metrics from cost schema
      BigDecimal pv = getPlannedValue(projectId);
      BigDecimal ev = getEarnedValue(projectId);
      BigDecimal ac = getActualCost(projectId);

      double spi = ev != null && pv != null && pv.signum() > 0
          ? ev.doubleValue() / pv.doubleValue()
          : 0.0;

      double cpi = ac != null && ev != null && ac.signum() > 0
          ? ev.doubleValue() / ac.doubleValue()
          : 0.0;

      BigDecimal bac = getProjectBudget(projectId);
      BigDecimal eac = ac != null && cpi > 0 && bac != null
          ? bac.divide(BigDecimal.valueOf(cpi), 2, java.math.RoundingMode.HALF_UP)
          : bac;

      BigDecimal etc = eac != null && ac != null
          ? eac.subtract(ac)
          : null;

      BigDecimal vac = bac != null && eac != null
          ? bac.subtract(eac)
          : null;

      double tcpi = eac != null && bac != null && bac.signum() > 0
          ? (bac.doubleValue() - ev.doubleValue()) / (eac.doubleValue() - ac.doubleValue())
          : 0.0;

      return new EvmReportData(
          projectName,
          pv != null ? pv : BigDecimal.ZERO,
          ev != null ? ev : BigDecimal.ZERO,
          ac != null ? ac : BigDecimal.ZERO,
          spi,
          cpi,
          eac != null ? eac : BigDecimal.ZERO,
          etc != null ? etc : BigDecimal.ZERO,
          vac != null ? vac : BigDecimal.ZERO,
          tcpi);
    } catch (Exception e) {
      log.error("Error generating EVM report for projectId={}", projectId, e);
      throw new RuntimeException("Failed to generate EVM report", e);
    }
  }

  @Transactional(readOnly = true)
  public List<CashFlowEntry> getCashFlowReport(UUID projectId) {
    try {
      return getCashFlowForecast(projectId);
    } catch (Exception e) {
      log.error("Error generating cash flow report for projectId={}", projectId, e);
      throw new RuntimeException("Failed to generate cash flow report", e);
    }
  }

  @Transactional(readOnly = true)
  public ContractStatusData getContractStatus(UUID projectId) {
    try {
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
          totalContractValue != null ? totalContractValue : BigDecimal.ZERO,
          totalVoValue != null ? totalVoValue : BigDecimal.ZERO,
          pendingMilestones,
          achievedMilestones,
          contracts);
    } catch (Exception e) {
      log.error("Error generating contract status report for projectId={}", projectId, e);
      throw new RuntimeException("Failed to generate contract status report", e);
    }
  }

  @Transactional(readOnly = true)
  public RiskRegisterData getRiskRegister(UUID projectId) {
    try {
      String projectName = getProjectName(projectId);
      int totalRisks = getTotalRisks(projectId);
      int highRisks = getRisksByLevel(projectId, "HIGH");
      int mediumRisks = getRisksByLevel(projectId, "MEDIUM");
      int lowRisks = getRisksByLevel(projectId, "LOW");
      Map<String, Integer> risksByCategory = getRisksByCategory(projectId);
      List<RiskRegisterData.RiskSummaryRow> topRisks = getTopRisks(projectId, 10);

      return new RiskRegisterData(
          projectName,
          totalRisks,
          highRisks,
          mediumRisks,
          lowRisks,
          risksByCategory,
          topRisks);
    } catch (Exception e) {
      log.error("Error generating risk register for projectId={}", projectId, e);
      throw new RuntimeException("Failed to generate risk register", e);
    }
  }

  @Transactional(readOnly = true)
  public ResourceUtilizationData getResourceUtilization(UUID projectId) {
    try {
      String projectName = getProjectName(projectId);
      int totalResources = getTotalResources(projectId);
      double avgUtilization = getAverageUtilization(projectId);
      List<ResourceUtilizationData.ResourceUtilRow> resources = getResourceUtilizationDetails(
          projectId);

      return new ResourceUtilizationData(
          projectName,
          totalResources,
          avgUtilization,
          resources);
    } catch (Exception e) {
      log.error("Error generating resource utilization report for projectId={}", projectId, e);
      throw new RuntimeException("Failed to generate resource utilization report", e);
    }
  }

  @Transactional(readOnly = true)
  public TrendAnalysisData getTrendAnalysis(UUID projectId, int months) {
    try {
      String projectName = getProjectName(projectId);

      // Period-over-period metrics
      List<TrendAnalysisData.PeriodMetric> periodMetrics = getPeriodMetrics(projectId, months);

      // Milestone status
      List<TrendAnalysisData.MilestoneStatusRow> milestoneStatus = getMilestoneStatus(projectId);

      // Activity status distribution
      Map<String, Integer> activityDistribution = getActivityDistribution(projectId);

      // Resource loading trend
      List<TrendAnalysisData.ResourceLoadingEntry> resourceLoadingTrend =
          getResourceLoadingTrend(projectId, months);

      return new TrendAnalysisData(
          projectName,
          periodMetrics,
          milestoneStatus,
          activityDistribution,
          resourceLoadingTrend);
    } catch (Exception e) {
      log.error("Error generating trend analysis for projectId={}", projectId, e);
      throw new RuntimeException("Failed to generate trend analysis report", e);
    }
  }

  private List<TrendAnalysisData.PeriodMetric> getPeriodMetrics(UUID projectId, int months) {
    List<TrendAnalysisData.PeriodMetric> metrics = new ArrayList<>();
    LocalDate now = LocalDate.now();

    for (int i = months - 1; i >= 0; i--) {
      YearMonth ym = YearMonth.from(now.minusMonths(i));
      String period = ym.toString();
      LocalDate monthEnd = ym.atEndOfMonth();

      try {
        int total = countActivitiesAsOf(projectId, monthEnd);
        int completed = countCompletedAsOf(projectId, monthEnd);
        double pct = total > 0 ? (completed * 100.0) / total : 0.0;

        double spi = getSpiAsOf(projectId, monthEnd);
        double cpi = getCpiAsOf(projectId, monthEnd);

        metrics.add(new TrendAnalysisData.PeriodMetric(period, total, completed, pct, spi, cpi));
      } catch (Exception e) {
        log.warn("Failed to fetch trend metrics for period {}: {}", period, e.getMessage());
        metrics.add(new TrendAnalysisData.PeriodMetric(period, 0, 0, 0.0, 0.0, 0.0));
      }
    }

    return metrics;
  }

  private int countActivitiesAsOf(UUID projectId, LocalDate asOf) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM activity.activities " +
              "WHERE project_id = ?1 AND planned_start_date <= ?2")
          .setParameter(1, projectId.toString())
          .setParameter(2, asOf)
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      log.warn("Failed to count activities for project={} asOf={}: {}", projectId, asOf, e.getMessage());
      return 0;
    }
  }

  private int countCompletedAsOf(UUID projectId, LocalDate asOf) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM activity.activities " +
              "WHERE project_id = ?1 AND status = 'COMPLETED' AND actual_finish_date <= ?2")
          .setParameter(1, projectId.toString())
          .setParameter(2, asOf)
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      log.warn("Failed to count completed activities for project={} asOf={}: {}", projectId, asOf, e.getMessage());
      return 0;
    }
  }

  private double getSpiAsOf(UUID projectId, LocalDate asOf) {
    try {
      Object result = em.createNativeQuery(
              "SELECT schedule_performance_index FROM evm.evm_calculations " +
              "WHERE project_id = ?1 AND data_date <= ?2 " +
              "ORDER BY data_date DESC LIMIT 1")
          .setParameter(1, projectId.toString())
          .setParameter(2, asOf)
          .getSingleResult();
      return result != null ? ((Number) result).doubleValue() : 0.0;
    } catch (Exception e) {
      log.warn("Failed to get SPI for project={} asOf={}: {}", projectId, asOf, e.getMessage());
      return 0.0;
    }
  }

  private double getCpiAsOf(UUID projectId, LocalDate asOf) {
    try {
      Object result = em.createNativeQuery(
              "SELECT cost_performance_index FROM evm.evm_calculations " +
              "WHERE project_id = ?1 AND data_date <= ?2 " +
              "ORDER BY data_date DESC LIMIT 1")
          .setParameter(1, projectId.toString())
          .setParameter(2, asOf)
          .getSingleResult();
      return result != null ? ((Number) result).doubleValue() : 0.0;
    } catch (Exception e) {
      log.warn("Failed to get CPI for project={} asOf={}: {}", projectId, asOf, e.getMessage());
      return 0.0;
    }
  }

  @SuppressWarnings("unchecked")
  private List<TrendAnalysisData.MilestoneStatusRow> getMilestoneStatus(UUID projectId) {
    try {
      List<Object> results = em.createNativeQuery(
              "SELECT cm.milestone_code, cm.milestone_name, cm.target_date, cm.actual_date, " +
              "CASE WHEN cm.actual_date IS NOT NULL THEN 'ACHIEVED' " +
              "     WHEN cm.target_date < CURRENT_DATE THEN 'LATE' " +
              "     ELSE 'PENDING' END as status, " +
              "COALESCE(EXTRACT(DAY FROM (cm.actual_date - cm.target_date)), " +
              "         EXTRACT(DAY FROM (CURRENT_DATE - cm.target_date))) as variance_days " +
              "FROM contract.contract_milestones cm " +
              "JOIN contract.contracts c ON cm.contract_id = c.id " +
              "WHERE c.project_id = ?1 " +
              "ORDER BY cm.target_date ASC")
          .setParameter(1, projectId.toString())
          .getResultList();

      return results.stream()
          .map(row -> {
            Object[] cols = (Object[]) row;
            return new TrendAnalysisData.MilestoneStatusRow(
                cols[0] != null ? cols[0].toString() : "",
                cols[1] != null ? cols[1].toString() : "",
                cols[2] != null ? cols[2].toString() : "",
                cols[3] != null ? cols[3].toString() : "",
                cols[4] != null ? cols[4].toString() : "PENDING",
                cols[5] != null ? ((Number) cols[5]).intValue() : 0);
          })
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Failed to get milestone status for project={}: {}", projectId, e.getMessage());
      return new ArrayList<>();
    }
  }

  private Map<String, Integer> getActivityDistribution(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> results = em.createNativeQuery(
              "SELECT status, COUNT(*) as cnt FROM activity.activities " +
              "WHERE project_id = ?1 GROUP BY status")
          .setParameter(1, projectId.toString())
          .getResultList();

      return results.stream()
          .collect(Collectors.toMap(
              row -> ((Object[]) row)[0] != null ? ((Object[]) row)[0].toString() : "UNKNOWN",
              row -> ((Number) ((Object[]) row)[1]).intValue()));
    } catch (Exception e) {
      log.warn("Failed to get activity distribution for project={}: {}", projectId, e.getMessage());
      return new HashMap<>();
    }
  }

  private List<TrendAnalysisData.ResourceLoadingEntry> getResourceLoadingTrend(
      UUID projectId, int months) {
    List<TrendAnalysisData.ResourceLoadingEntry> entries = new ArrayList<>();
    LocalDate now = LocalDate.now();

    for (int i = months - 1; i >= 0; i--) {
      YearMonth ym = YearMonth.from(now.minusMonths(i));
      String period = ym.toString();
      LocalDate monthStart = ym.atDay(1);
      LocalDate monthEnd = ym.atEndOfMonth();

      try {
        @SuppressWarnings("unchecked")
        List<Object> results = em.createNativeQuery(
                "SELECT COALESCE(SUM(planned_units), 0), COALESCE(SUM(actual_units), 0), " +
                "COUNT(DISTINCT resource_id) " +
                "FROM resource.resource_assignments " +
                "WHERE project_id = ?1 AND planned_start_date <= ?3 AND planned_finish_date >= ?2")
            .setParameter(1, projectId.toString())
            .setParameter(2, monthStart)
            .setParameter(3, monthEnd)
            .getResultList();

        if (!results.isEmpty()) {
          Object[] cols = (Object[]) results.get(0);
          entries.add(new TrendAnalysisData.ResourceLoadingEntry(
              period,
              cols[0] != null ? ((Number) cols[0]).doubleValue() : 0.0,
              cols[1] != null ? ((Number) cols[1]).doubleValue() : 0.0,
              cols[2] != null ? ((Number) cols[2]).intValue() : 0));
        } else {
          entries.add(new TrendAnalysisData.ResourceLoadingEntry(period, 0.0, 0.0, 0));
        }
      } catch (Exception e) {
        log.warn("Failed to get resource loading for period {}: {}", period, e.getMessage());
        entries.add(new TrendAnalysisData.ResourceLoadingEntry(period, 0.0, 0.0, 0));
      }
    }

    return entries;
  }

  // Helper methods for querying data from different schemas

  private String getProjectName(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT name FROM project.projects WHERE id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? result.toString() : "Unknown Project";
    } catch (Exception e) {
      log.warn("Could not fetch project name for projectId={}", projectId);
      return "Project " + projectId;
    }
  }

  private String getProjectCode(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT code FROM project.projects WHERE id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? result.toString() : "N/A";
    } catch (Exception e) {
      return "N/A";
    }
  }

  private int getTotalActivities(UUID projectId, LocalDate start, LocalDate end) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM activity.activities " +
              "WHERE project_id = ?1 AND actual_start_date <= ?3 AND actual_finish_date >= ?2")
          .setParameter(1, projectId.toString())
          .setParameter(2, start)
          .setParameter(3, end)
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private int getCompletedActivities(UUID projectId, LocalDate start, LocalDate end) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM activity.activities " +
              "WHERE project_id = ?1 AND status = 'COMPLETED'")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private int getInProgressActivities(UUID projectId, LocalDate start, LocalDate end) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM activity.activities " +
              "WHERE project_id = ?1 AND status = 'IN_PROGRESS'")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private BigDecimal getProjectBudget(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT SUM(budgeted_cost) FROM cost.activity_expenses WHERE project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private BigDecimal getActualCost(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT SUM(actual_cost) FROM cost.activity_expenses WHERE project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private BigDecimal getActualCost(UUID projectId, LocalDate start, LocalDate end) {
    try {
      Object result = em.createNativeQuery(
              "SELECT SUM(actual_cost) FROM cost.activity_expenses " +
              "WHERE project_id = ?1 AND planned_start_date BETWEEN ?2 AND ?3")
          .setParameter(1, projectId.toString())
          .setParameter(2, start)
          .setParameter(3, end)
          .getSingleResult();
      return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private BigDecimal getForecastCost(UUID projectId, LocalDate start, LocalDate end) {
    try {
      Object result = em.createNativeQuery(
              "SELECT SUM(at_completion_cost) FROM cost.activity_expenses " +
              "WHERE project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private BigDecimal getPlannedValue(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT planned_value FROM evm.evm_calculations WHERE project_id = ?1 " +
              "ORDER BY data_date DESC LIMIT 1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private BigDecimal getEarnedValue(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT earned_value FROM evm.evm_calculations WHERE project_id = ?1 " +
              "ORDER BY data_date DESC LIMIT 1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private int getTotalMilestones(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM contract.contract_milestones cm " +
              "JOIN contract.contracts c ON cm.contract_id = c.id WHERE c.project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private int getAchievedMilestones(UUID projectId, LocalDate asOfDate) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM contract.contract_milestones cm " +
              "JOIN contract.contracts c ON cm.contract_id = c.id " +
              "WHERE c.project_id = ?1 AND cm.actual_date IS NOT NULL AND cm.actual_date <= ?2")
          .setParameter(1, projectId.toString())
          .setParameter(2, asOfDate)
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private int getAchievedMilestones(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM contract.contract_milestones cm " +
              "JOIN contract.contracts c ON cm.contract_id = c.id " +
              "WHERE c.project_id = ?1 AND cm.actual_date IS NOT NULL")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private int getOpenRisks(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM risk.risks WHERE project_id = ?1 AND status IN ('OPEN', 'ACTIVE')")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private int getHighRisks(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM risk.risks WHERE project_id = ?1 AND severity = 'HIGH'")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private List<MonthlyProgressData.ActivitySummaryRow> getTopDelayedActivities(
      UUID projectId, int limit, LocalDate asOfDate) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> results = em.createNativeQuery(
              "SELECT a.code, a.name, a.status, " +
              "EXTRACT(DAY FROM (a.planned_finish_date - ?2)) as total_float, a.planned_finish_date " +
              "FROM activity.activities a " +
              "WHERE a.project_id = ?1 AND a.actual_finish_date IS NULL " +
              "AND a.planned_finish_date < ?2 " +
              "ORDER BY a.planned_finish ASC LIMIT ?3")
          .setParameter(1, projectId.toString())
          .setParameter(2, asOfDate)
          .setParameter(3, limit)
          .getResultList();

      return results.stream()
          .map(row -> {
            Object[] cols = (Object[]) row;
            return new MonthlyProgressData.ActivitySummaryRow(
                cols[0] != null ? cols[0].toString() : "",
                cols[1] != null ? cols[1].toString() : "",
                cols[2] != null ? cols[2].toString() : "",
                cols[3] != null ? ((Number) cols[3]).doubleValue() : 0.0,
                cols[4] instanceof LocalDate ld ? ld : null);
          })
          .collect(Collectors.toList());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private List<CashFlowEntry> getCashFlowForecast(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> results = em.createNativeQuery(
              "SELECT period, planned_amount, actual_amount, forecast_amount, " +
              "cum_planned, cum_actual, cum_forecast " +
              "FROM cost.cash_flow_forecasts " +
              "WHERE project_id = ?1 ORDER BY period ASC")
          .setParameter(1, projectId.toString())
          .getResultList();

      return results.stream()
          .map(row -> {
            Object[] cols = (Object[]) row;
            return new CashFlowEntry(
                cols[0] != null ? cols[0].toString() : "",
                cols[1] != null ? new BigDecimal(cols[1].toString()) : BigDecimal.ZERO,
                cols[2] != null ? new BigDecimal(cols[2].toString()) : BigDecimal.ZERO,
                cols[3] != null ? new BigDecimal(cols[3].toString()) : BigDecimal.ZERO,
                cols[4] != null ? new BigDecimal(cols[4].toString()) : BigDecimal.ZERO,
                cols[5] != null ? new BigDecimal(cols[5].toString()) : BigDecimal.ZERO,
                cols[6] != null ? new BigDecimal(cols[6].toString()) : BigDecimal.ZERO);
          })
          .collect(Collectors.toList());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private int getTotalContracts(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM contract.contracts WHERE project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private int getActiveContracts(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM contract.contracts " +
              "WHERE project_id = ?1 AND status IN ('ACTIVE', 'ONGOING')")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private BigDecimal getTotalContractValue(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT SUM(contract_value) FROM contract.contracts WHERE project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private BigDecimal getTotalVariationOrderValue(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT SUM(vo.vo_value) FROM contract.variation_orders vo " +
              "JOIN contract.contracts c ON vo.contract_id = c.id WHERE c.project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private int getPendingMilestones(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM contract.contract_milestones cm " +
              "JOIN contract.contracts c ON cm.contract_id = c.id " +
              "WHERE c.project_id = ?1 AND cm.status IN ('PENDING', 'DUE')")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private List<ContractStatusData.ContractSummaryRow> getContractSummaries(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> results = em.createNativeQuery(
              "SELECT c.contract_number, c.contractor_name, c.contract_value, c.status, " +
              "COUNT(cm.id) as pending_count " +
              "FROM contract.contracts c " +
              "LEFT JOIN contract.contract_milestones cm ON c.id = cm.contract_id " +
              "WHERE c.project_id = ?1 " +
              "GROUP BY c.id ORDER BY c.created_at DESC LIMIT 10")
          .setParameter(1, projectId.toString())
          .getResultList();

      return results.stream()
          .map(row -> {
            Object[] cols = (Object[]) row;
            return new ContractStatusData.ContractSummaryRow(
                cols[0] != null ? cols[0].toString() : "",
                cols[1] != null ? cols[1].toString() : "",
                cols[2] != null ? new BigDecimal(cols[2].toString()) : BigDecimal.ZERO,
                cols[3] != null ? cols[3].toString() : "",
                cols[4] != null ? ((Number) cols[4]).intValue() : 0);
          })
          .collect(Collectors.toList());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private int getTotalRisks(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM risk.risks WHERE project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private int getRisksByLevel(UUID projectId, String severity) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(*) FROM risk.risks WHERE project_id = ?1 AND severity = ?2")
          .setParameter(1, projectId.toString())
          .setParameter(2, severity)
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private Map<String, Integer> getRisksByCategory(UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> results = em.createNativeQuery(
              "SELECT category, COUNT(*) as risk_count FROM risk.risks " +
              "WHERE project_id = ?1 GROUP BY category")
          .setParameter(1, projectId.toString())
          .getResultList();

      return results.stream()
          .collect(Collectors.toMap(
              row -> ((Object[]) row)[0].toString(),
              row -> ((Number) ((Object[]) row)[1]).intValue()));
    } catch (Exception e) {
      return new HashMap<>();
    }
  }

  private List<RiskRegisterData.RiskSummaryRow> getTopRisks(UUID projectId, int limit) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> results = em.createNativeQuery(
              "SELECT code, title, category, probability, impact, risk_score " +
              "FROM risk.risks WHERE project_id = ?1 " +
              "ORDER BY risk_score DESC LIMIT ?2")
          .setParameter(1, projectId.toString())
          .setParameter(2, limit)
          .getResultList();

      return results.stream()
          .map(row -> {
            Object[] cols = (Object[]) row;
            return new RiskRegisterData.RiskSummaryRow(
                cols[0] != null ? cols[0].toString() : "",
                cols[1] != null ? cols[1].toString() : "",
                cols[2] != null ? cols[2].toString() : "",
                cols[3] != null ? cols[3].toString() : "",
                cols[4] != null ? cols[4].toString() : "",
                cols[5] != null ? ((Number) cols[5]).doubleValue() : 0.0);
          })
          .collect(Collectors.toList());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private int getTotalResources(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT COUNT(DISTINCT resource_id) FROM resource.resource_assignments WHERE project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).intValue() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private double getAverageUtilization(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT CASE WHEN SUM(planned_units) > 0 " +
              "THEN ROUND(100.0 * SUM(actual_units) / SUM(planned_units), 2) ELSE 0 END " +
              "FROM resource.resource_assignments WHERE project_id = ?1")
          .setParameter(1, projectId.toString())
          .getSingleResult();
      return result != null ? ((Number) result).doubleValue() : 0.0;
    } catch (Exception e) {
      return 0.0;
    }
  }

  private List<ResourceUtilizationData.ResourceUtilRow> getResourceUtilizationDetails(
      UUID projectId) {
    try {
      @SuppressWarnings("unchecked")
      List<Object> results = em.createNativeQuery(
              "SELECT r.code, r.name, r.resource_type, " +
              "COALESCE(SUM(a.planned_units), 0) as planned_units, " +
              "COALESCE(SUM(a.actual_units), 0) as actual_units, " +
              "CASE WHEN SUM(a.planned_units) > 0 THEN ROUND(100.0 * SUM(a.actual_units) / SUM(a.planned_units), 2) ELSE 0 END as util_pct " +
              "FROM resource.resources r " +
              "LEFT JOIN resource.resource_assignments a ON r.id = a.resource_id " +
              "WHERE a.project_id = ?1 " +
              "GROUP BY r.id ORDER BY util_pct DESC LIMIT 50")
          .setParameter(1, projectId.toString())
          .getResultList();

      return results.stream()
          .map(row -> {
            Object[] cols = (Object[]) row;
            return new ResourceUtilizationData.ResourceUtilRow(
                cols[0] != null ? cols[0].toString() : "",
                cols[1] != null ? cols[1].toString() : "",
                cols[2] != null ? cols[2].toString() : "",
                cols[3] != null ? ((Number) cols[3]).doubleValue() : 0.0,
                cols[4] != null ? ((Number) cols[4]).doubleValue() : 0.0,
                cols[5] != null ? ((Number) cols[5]).doubleValue() : 0.0);
          })
          .collect(Collectors.toList());
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }
}
