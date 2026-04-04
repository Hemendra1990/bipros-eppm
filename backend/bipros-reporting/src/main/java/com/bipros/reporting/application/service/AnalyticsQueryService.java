package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.AnalyticsQueryDto;
import com.bipros.reporting.application.dto.AnalyticsQueryRequest;
import com.bipros.reporting.domain.model.AnalyticsQuery;
import com.bipros.reporting.domain.repository.AnalyticsQueryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsQueryService {

  private final AnalyticsQueryRepository analyticsQueryRepository;

  @PersistenceContext private EntityManager em;

  /**
   * Process a natural language query and generate a response
   */
  @Transactional
  public AnalyticsQueryDto processQuery(AnalyticsQueryRequest request, String userId) {
    long startTime = System.currentTimeMillis();

    try {
      String queryText = request.getQueryText().toLowerCase();
      String queryType;
      String response;

      if (queryText.contains("cost") || queryText.contains("budget") ||
          queryText.contains("overrun") || queryText.contains("expense")) {
        queryType = "COST_QUERY";
        response = generateCostResponse(request);
      } else if (queryText.contains("schedule") || queryText.contains("delay") ||
          queryText.contains("slip") || queryText.contains("timeline")) {
        queryType = "SCHEDULE_QUERY";
        response = generateScheduleResponse(request);
      } else if (queryText.contains("risk")) {
        queryType = "RISK_QUERY";
        response = generateRiskResponse(request);
      } else if (queryText.contains("resource") || queryText.contains("equipment") ||
          queryText.contains("labour") || queryText.contains("labor") ||
          queryText.contains("material")) {
        queryType = "RESOURCE_QUERY";
        response = generateResourceResponse(request);
      } else if (queryText.contains("status") || queryText.contains("progress") ||
          queryText.contains("completion") || queryText.contains("health")) {
        queryType = "PROJECT_STATUS";
        response = generateStatusResponse(request);
      } else {
        queryType = "GENERAL";
        response = "I can help with queries about project cost, schedule, risk, resources, and "
            + "project status. Try asking: 'What is the cost overrun risk?', 'Which projects are "
            + "delayed?', 'Show schedule summary', or 'What are the top risks?'";
      }

      long responseTimeMs = System.currentTimeMillis() - startTime;

      AnalyticsQuery query = new AnalyticsQuery();
      query.setUserId(userId);
      query.setQueryText(request.getQueryText());
      query.setQueryType(AnalyticsQuery.QueryType.valueOf(queryType));
      query.setResponseText(response);
      query.setResponseTimeMs(responseTimeMs);

      AnalyticsQuery saved = analyticsQueryRepository.save(query);
      return AnalyticsQueryDto.from(saved);
    } catch (Exception e) {
      log.error("Error processing analytics query: {}", request.getQueryText(), e);
      long responseTimeMs = System.currentTimeMillis() - startTime;

      AnalyticsQuery query = new AnalyticsQuery();
      query.setUserId(userId);
      query.setQueryText(request.getQueryText());
      query.setQueryType(AnalyticsQuery.QueryType.GENERAL);
      query.setResponseText("Sorry, I encountered an error processing your query. Please try "
          + "again or rephrase your question.");
      query.setResponseTimeMs(responseTimeMs);

      AnalyticsQuery saved = analyticsQueryRepository.save(query);
      return AnalyticsQueryDto.from(saved);
    }
  }

  /**
   * Get query history for a user
   */
  @Transactional(readOnly = true)
  public List<AnalyticsQueryDto> getQueryHistory(String userId, int limit) {
    return analyticsQueryRepository.findByUserIdOrderByCreatedAtDesc(userId,
        PageRequest.of(0, limit)).stream()
        .map(AnalyticsQueryDto::from)
        .collect(Collectors.toList());
  }

  // Response generation methods

  private String generateCostResponse(AnalyticsQueryRequest request) {
    try {
      Map<String, Object> costData = getCostMetrics(request.getProjectId());

      double budgetUtilization = (double) costData.getOrDefault("budgetUtilization", 0.0);
      double cpi = (double) costData.getOrDefault("cpi", 1.0);
      double eac = (double) costData.getOrDefault("eac", 0.0);
      double bac = (double) costData.getOrDefault("bac", 0.0);

      String health = cpi >= 0.95 ? "green" : cpi >= 0.85 ? "amber" : "red";
      String trend = "stable";
      if (cpi < 0.9) {
        trend = "cost overrun expected";
      }

      return String.format(
          "Cost Status: %s\n"
              + "Budget Utilization: %.1f%%\n"
              + "Cost Performance Index (CPI): %.2f\n"
              + "Budget at Completion (BAC): $%.0f\n"
              + "Estimated at Completion (EAC): $%.0f\n"
              + "Trend: %s\n\n"
              + "Based on current metrics, your project %s.",
          health.toUpperCase(),
          budgetUtilization,
          cpi,
          bac,
          eac,
          trend,
          "is on track for cost");
    } catch (Exception e) {
      log.warn("Error generating cost response", e);
      return "Unable to generate cost analysis. Please check your data and try again.";
    }
  }

  private String generateScheduleResponse(AnalyticsQueryRequest request) {
    try {
      Map<String, Object> scheduleData = getScheduleMetrics(request.getProjectId());

      double spi = (double) scheduleData.getOrDefault("spi", 1.0);
      int delayedActivities = (int) scheduleData.getOrDefault("delayedActivities", 0);
      double completionPercentage = (double) scheduleData.getOrDefault("completionPercentage",
          0.0);
      int daysSlippage = (int) scheduleData.getOrDefault("estimatedSlippage", 0);

      String health = spi >= 0.95 ? "green" : spi >= 0.85 ? "amber" : "red";

      return String.format(
          "Schedule Status: %s\n"
              + "Schedule Performance Index (SPI): %.2f\n"
              + "Overall Progress: %.1f%%\n"
              + "Delayed Activities: %d\n"
              + "Estimated Schedule Slippage: %d days\n\n"
              + "Your project is currently %s on schedule. %s",
          health.toUpperCase(),
          spi,
          completionPercentage,
          delayedActivities,
          Math.abs(daysSlippage),
          spi >= 1.0 ? "ahead of" : "behind",
          spi < 0.9 ? "Immediate action recommended to address schedule delays."
              : "Continue monitoring critical path activities.");
    } catch (Exception e) {
      log.warn("Error generating schedule response", e);
      return "Unable to generate schedule analysis. Please check your data and try again.";
    }
  }

  private String generateRiskResponse(AnalyticsQueryRequest request) {
    try {
      Map<String, Object> riskData = getRiskMetrics(request.getProjectId());

      int totalRisks = (int) riskData.getOrDefault("totalRisks", 0);
      int highRisks = (int) riskData.getOrDefault("highRisks", 0);
      int mediumRisks = (int) riskData.getOrDefault("mediumRisks", 0);
      double overallRiskScore = (double) riskData.getOrDefault("overallRiskScore", 0.0);

      return String.format(
          "Risk Summary:\n"
              + "Total Risks: %d\n"
              + "High Priority: %d\n"
              + "Medium Priority: %d\n"
              + "Overall Risk Score: %.1f/100\n\n"
              + "Risk Level: %s\n\n"
              + "%s",
          totalRisks,
          highRisks,
          mediumRisks,
          overallRiskScore,
          overallRiskScore > 70 ? "CRITICAL" : overallRiskScore > 50 ? "HIGH" : "MODERATE",
          highRisks > 0 ? "Review and prioritize high-risk items immediately." : "Continue "
              + "regular risk monitoring.");
    } catch (Exception e) {
      log.warn("Error generating risk response", e);
      return "Unable to generate risk analysis. Please check your data and try again.";
    }
  }

  private String generateResourceResponse(AnalyticsQueryRequest request) {
    try {
      Map<String, Object> resourceData = getResourceMetrics(request.getProjectId());

      int totalResources = (int) resourceData.getOrDefault("totalResources", 0);
      int allocatedResources = (int) resourceData.getOrDefault("allocatedResources", 0);
      double allocationRate = (double) resourceData.getOrDefault("allocationRate", 0.0);

      return String.format(
          "Resource Summary:\n"
              + "Total Resources: %d\n"
              + "Allocated Resources: %d\n"
              + "Allocation Rate: %.1f%%\n\n"
              + "Resource Status: %s\n\n"
              + "%s",
          totalResources,
          allocatedResources,
          allocationRate,
          allocationRate > 80 ? "Fully Utilized" : "Partially Utilized",
          allocationRate < 60 ? "Consider utilizing available resources for better efficiency."
              : "Resource allocation looks good.");
    } catch (Exception e) {
      log.warn("Error generating resource response", e);
      return "Unable to generate resource analysis. Please check your data and try again.";
    }
  }

  private String generateStatusResponse(AnalyticsQueryRequest request) {
    try {
      Map<String, Object> statusData = getProjectStatus(request.getProjectId());

      String projectName = (String) statusData.getOrDefault("projectName", "Project");
      double overallHealth = (double) statusData.getOrDefault("overallHealth", 0.0);
      String status = (String) statusData.getOrDefault("status", "In Progress");

      return String.format(
          "Project Status: %s\n"
              + "Project Name: %s\n"
              + "Current Status: %s\n"
              + "Overall Health: %.1f%%\n\n"
              + "Health Indicators:\n"
              + "✓ Schedule: %s\n"
              + "✓ Cost: %s\n"
              + "✓ Risk: %s\n",
          overallHealth > 75 ? "HEALTHY" : overallHealth > 50 ? "AT RISK" : "CRITICAL",
          projectName,
          status,
          overallHealth,
          (boolean) statusData.getOrDefault("scheduleHealthy", true) ? "On Track" : "Behind",
          (boolean) statusData.getOrDefault("costHealthy", true) ? "On Budget" : "Over Budget",
          (boolean) statusData.getOrDefault("riskHealthy", true) ? "Controlled" : "Escalating");
    } catch (Exception e) {
      log.warn("Error generating status response", e);
      return "Unable to generate project status. Please check your data and try again.";
    }
  }

  // Data collection methods

  private Map<String, Object> getCostMetrics(java.util.UUID projectId) {
    Map<String, Object> metrics = new HashMap<>();
    try {
      if (projectId == null) {
        return metrics;
      }

      Object[] result = (Object[]) em.createNativeQuery(
          "SELECT CAST(COALESCE(SUM(contract_value), 0) AS DOUBLE PRECISION), "
              + "CAST(COALESCE(SUM(actual_cost), 0) AS DOUBLE PRECISION), "
              + "CAST(COALESCE(SUM(earned_value), 0) AS DOUBLE PRECISION) "
              + "FROM contracts.contracts WHERE project_id = ?"
      )
          .setParameter(1, projectId)
          .getSingleResult();

      double bac = ((Number) result[0]).doubleValue();
      double ac = ((Number) result[1]).doubleValue();
      double ev = ((Number) result[2]).doubleValue();

      double cpi = ac > 0 ? ev / ac : 1.0;
      double eac = ac > 0 ? bac / cpi : bac;
      double budgetUtilization = bac > 0 ? (ac / bac) * 100 : 0.0;

      metrics.put("bac", bac);
      metrics.put("ac", ac);
      metrics.put("ev", ev);
      metrics.put("cpi", cpi);
      metrics.put("eac", eac);
      metrics.put("budgetUtilization", budgetUtilization);
    } catch (Exception e) {
      log.debug("Could not retrieve cost metrics for projectId={}", projectId);
    }
    return metrics;
  }

  private Map<String, Object> getScheduleMetrics(java.util.UUID projectId) {
    Map<String, Object> metrics = new HashMap<>();
    try {
      if (projectId == null) {
        return metrics;
      }

      Object[] result = (Object[]) em.createNativeQuery(
          "SELECT CAST(COALESCE(SUM(pv), 0) AS DOUBLE PRECISION), "
              + "CAST(COALESCE(SUM(ev), 0) AS DOUBLE PRECISION) "
              + "FROM evm.earned_value_summary WHERE project_id = ? "
              + "ORDER BY calculated_at DESC LIMIT 1"
      )
          .setParameter(1, projectId)
          .getSingleResult();

      double pv = ((Number) result[0]).doubleValue();
      double ev = ((Number) result[1]).doubleValue();
      double spi = pv > 0 ? ev / pv : 1.0;

      Integer delayedCount = (Integer) em.createNativeQuery(
          "SELECT COUNT(*) FROM scheduling.activities WHERE project_id = ? "
              + "AND status = 'NOT_STARTED' AND planned_start < CURRENT_DATE"
      )
          .setParameter(1, projectId)
          .getSingleResult();

      metrics.put("spi", spi);
      metrics.put("delayedActivities", delayedCount != null ? delayedCount : 0);
      metrics.put("completionPercentage", Math.min(ev / (pv > 0 ? pv : 1.0) * 100, 100.0));
      metrics.put("estimatedSlippage", (int) ((1.0 / spi - 1.0) * 10));
    } catch (Exception e) {
      log.debug("Could not retrieve schedule metrics for projectId={}", projectId);
    }
    return metrics;
  }

  private Map<String, Object> getRiskMetrics(java.util.UUID projectId) {
    Map<String, Object> metrics = new HashMap<>();
    try {
      if (projectId == null) {
        return metrics;
      }

      Integer totalRisks = (Integer) em.createNativeQuery(
          "SELECT COUNT(*) FROM risk.risks WHERE project_id = ?"
      )
          .setParameter(1, projectId)
          .getSingleResult();

      Integer highRisks = (Integer) em.createNativeQuery(
          "SELECT COUNT(*) FROM risk.risks WHERE project_id = ? AND priority = 'HIGH'"
      )
          .setParameter(1, projectId)
          .getSingleResult();

      Integer mediumRisks = (Integer) em.createNativeQuery(
          "SELECT COUNT(*) FROM risk.risks WHERE project_id = ? AND priority = 'MEDIUM'"
      )
          .setParameter(1, projectId)
          .getSingleResult();

      double riskScore = (highRisks != null ? highRisks : 0) * 20.0
          + (mediumRisks != null ? mediumRisks : 0) * 10.0;
      riskScore = Math.min(riskScore, 100.0);

      metrics.put("totalRisks", totalRisks != null ? totalRisks : 0);
      metrics.put("highRisks", highRisks != null ? highRisks : 0);
      metrics.put("mediumRisks", mediumRisks != null ? mediumRisks : 0);
      metrics.put("overallRiskScore", riskScore);
    } catch (Exception e) {
      log.debug("Could not retrieve risk metrics for projectId={}", projectId);
    }
    return metrics;
  }

  private Map<String, Object> getResourceMetrics(java.util.UUID projectId) {
    Map<String, Object> metrics = new HashMap<>();
    try {
      if (projectId == null) {
        return metrics;
      }

      Integer totalResources = (Integer) em.createNativeQuery(
          "SELECT COUNT(*) FROM resource.resources WHERE project_id = ?"
      )
          .setParameter(1, projectId)
          .getSingleResult();

      Integer allocatedResources = (Integer) em.createNativeQuery(
          "SELECT COUNT(*) FROM resource.resource_assignments WHERE project_id = ? "
              + "AND status = 'ACTIVE'"
      )
          .setParameter(1, projectId)
          .getSingleResult();

      double allocationRate = totalResources != null && totalResources > 0
          ? ((allocatedResources != null ? allocatedResources : 0) * 100.0) / totalResources
          : 0.0;

      metrics.put("totalResources", totalResources != null ? totalResources : 0);
      metrics.put("allocatedResources", allocatedResources != null ? allocatedResources : 0);
      metrics.put("allocationRate", allocationRate);
    } catch (Exception e) {
      log.debug("Could not retrieve resource metrics for projectId={}", projectId);
    }
    return metrics;
  }

  private Map<String, Object> getProjectStatus(java.util.UUID projectId) {
    Map<String, Object> status = new HashMap<>();
    try {
      if (projectId == null) {
        return status;
      }

      Object[] result = (Object[]) em.createNativeQuery(
          "SELECT project_name, status FROM project.projects WHERE id = ?"
      )
          .setParameter(1, projectId)
          .getSingleResult();

      status.put("projectName", result[0] != null ? result[0].toString() : "Project");
      status.put("status", result[1] != null ? result[1].toString() : "In Progress");

      Map<String, Object> costMetrics = getCostMetrics(projectId);
      Map<String, Object> scheduleMetrics = getScheduleMetrics(projectId);
      Map<String, Object> riskMetrics = getRiskMetrics(projectId);

      boolean costHealthy = ((Number) costMetrics.getOrDefault("cpi", 1.0))
          .doubleValue() >= 0.9;
      boolean scheduleHealthy = ((Number) scheduleMetrics.getOrDefault("spi", 1.0))
          .doubleValue() >= 0.9;
      boolean riskHealthy = ((Number) riskMetrics.getOrDefault("overallRiskScore", 0.0))
          .doubleValue() < 50;

      double overallHealth = (costHealthy ? 33.3 : 0) + (scheduleHealthy ? 33.3 : 0)
          + (riskHealthy ? 33.4 : 0);

      status.put("costHealthy", costHealthy);
      status.put("scheduleHealthy", scheduleHealthy);
      status.put("riskHealthy", riskHealthy);
      status.put("overallHealth", overallHealth);
    } catch (Exception e) {
      log.debug("Could not retrieve project status for projectId={}", projectId);
    }
    return status;
  }
}
