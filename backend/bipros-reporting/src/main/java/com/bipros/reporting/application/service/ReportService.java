package com.bipros.reporting.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.reporting.application.dto.*;
import com.bipros.reporting.domain.model.*;
import com.bipros.reporting.domain.repository.ReportDefinitionRepository;
import com.bipros.reporting.domain.repository.ReportExecutionRepository;
import com.bipros.reporting.infrastructure.export.ExcelReportGenerator;
import com.bipros.reporting.infrastructure.export.PdfReportGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final ReportExecutionRepository reportExecutionRepository;
  private final ObjectMapper objectMapper;
  private final ExcelReportGenerator excelReportGenerator;
  private final PdfReportGenerator pdfReportGenerator;
  private final com.bipros.reporting.application.service.ReportDataService reportDataService;

  @PersistenceContext private EntityManager em;

  @Transactional
  public ReportDefinitionResponse createReportDefinition(CreateReportDefinitionRequest request) {
    var entity = new ReportDefinition();
    entity.setName(request.name());
    entity.setDescription(request.description());
    entity.setReportType(request.reportType());
    entity.setIsBuiltIn(false);
    entity.setConfigJson(request.configJson() != null ? request.configJson() : "{}");

    var saved = reportDefinitionRepository.save(entity);
    return ReportDefinitionResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<ReportDefinitionResponse> listReportDefinitions(ReportType typeFilter) {
    List<ReportDefinition> definitions;
    if (typeFilter != null) {
      definitions = reportDefinitionRepository.findByReportType(typeFilter);
    } else {
      definitions = reportDefinitionRepository.findAll();
    }
    return definitions.stream().map(ReportDefinitionResponse::from).collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public ReportDefinitionResponse getReportDefinition(UUID id) {
    var entity =
        reportDefinitionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", id));
    return ReportDefinitionResponse.from(entity);
  }

  @Transactional
  public void deleteReportDefinition(UUID id) {
    var entity =
        reportDefinitionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", id));
    reportDefinitionRepository.delete(entity);
  }

  @Transactional
  public ReportExecutionResponse executeReport(
      UUID definitionId, UUID projectId, ReportFormat format, Map<String, String> parameters) {
    var definition =
        reportDefinitionRepository
            .findById(definitionId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", definitionId));

    var execution = new ReportExecution();
    execution.setReportDefinitionId(definitionId);
    execution.setProjectId(projectId);
    execution.setFormat(format);
    execution.setStatus(ReportStatus.PENDING);
    execution.setExecutedAt(Instant.now());

    try {
      if (parameters != null) {
        try {
          execution.setParameters(objectMapper.writeValueAsString(parameters));
        } catch (Exception e) {
          log.warn("Failed to serialize parameters", e);
        }
      }

      execution.setStatus(ReportStatus.GENERATING);
      var saved = reportExecutionRepository.save(execution);

      String resultData = generateReportData(definition.getReportType(), projectId, parameters);
      byte[] reportBytes = null;

      switch (format) {
        case JSON -> saved.setResultData(resultData);
        case EXCEL -> reportBytes = generateExcelReport(definition.getReportType(), projectId);
        case PDF -> reportBytes = generatePdfReport(definition.getReportType(), projectId);
        case CSV -> log.info("CSV format not yet implemented");
      }

      if (reportBytes != null) {
        String fileName = generateFileName(definition.getReportType(), format);
        saved.setFilePath(fileName);
      }

      saved.setStatus(ReportStatus.COMPLETED);
      saved.setCompletedAt(Instant.now());

      var result = reportExecutionRepository.save(saved);
      return ReportExecutionResponse.from(result);
    } catch (Exception e) {
      log.error("Error executing report: definitionId={}, projectId={}", definitionId, projectId, e);
      execution.setStatus(ReportStatus.FAILED);
      execution.setErrorMessage(e.getMessage());
      execution.setCompletedAt(Instant.now());
      reportExecutionRepository.save(execution);
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public ReportExecutionResponse getExecution(UUID executionId) {
    var entity =
        reportExecutionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResourceNotFoundException("ReportExecution", executionId));
    return ReportExecutionResponse.from(entity);
  }

  @Transactional(readOnly = true)
  public List<ReportExecutionResponse> listExecutions(UUID projectId) {
    var executions = reportExecutionRepository.findByProjectIdOrderByExecutedAtDesc(projectId);
    return executions.stream().map(ReportExecutionResponse::from).collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<SCurveDataPoint> generateSCurveData(
      UUID projectId, LocalDate start, LocalDate end, String interval) {
    var dataPoints = new ArrayList<SCurveDataPoint>();

    LocalDate current = start;
    while (!current.isAfter(end)) {
      LocalDate periodEnd = switch (interval) {
        case "WEEKLY" -> current.plusWeeks(1).minusDays(1);
        case "MONTHLY" -> current.plusMonths(1).minusDays(1);
        default -> current;
      };
      if (periodEnd.isAfter(end)) periodEnd = end;

      try {
        // PV: cumulative budgeted cost of work scheduled up to this date
        Object pvResult = em.createNativeQuery(
                "SELECT COALESCE(SUM(budgeted_cost), 0) FROM cost.activity_expenses e " +
                "JOIN activity.activities a ON e.activity_id = a.id " +
                "WHERE e.project_id = ?1 AND a.planned_finish_date <= ?2")
            .setParameter(1, projectId)
            .setParameter(2, periodEnd)
            .getSingleResult();
        BigDecimal pv = pvResult != null ? new BigDecimal(pvResult.toString()) : BigDecimal.ZERO;

        // EV: cumulative budgeted cost * percent complete for activities in progress/complete
        Object evResult = em.createNativeQuery(
                "SELECT COALESCE(SUM(budgeted_cost * percent_complete / 100.0), 0) FROM cost.activity_expenses " +
                "WHERE project_id = ?1 AND planned_start_date <= ?2")
            .setParameter(1, projectId)
            .setParameter(2, periodEnd)
            .getSingleResult();
        BigDecimal ev = evResult != null ? new BigDecimal(evResult.toString()) : BigDecimal.ZERO;

        // AC: cumulative actual cost up to this date
        Object acResult = em.createNativeQuery(
                "SELECT COALESCE(SUM(actual_cost), 0) FROM cost.activity_expenses " +
                "WHERE project_id = ?1 AND planned_start_date <= ?2")
            .setParameter(1, projectId)
            .setParameter(2, periodEnd)
            .getSingleResult();
        BigDecimal ac = acResult != null ? new BigDecimal(acResult.toString()) : BigDecimal.ZERO;

        dataPoints.add(new SCurveDataPoint(current, pv, ev, ac));
      } catch (Exception e) {
        dataPoints.add(new SCurveDataPoint(current, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
      }

      current = switch (interval) {
        case "WEEKLY" -> current.plusWeeks(1);
        case "MONTHLY" -> current.plusMonths(1);
        default -> current.plusDays(1);
      };
    }

    return dataPoints;
  }

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<ResourceHistogramEntry> generateResourceHistogram(
      UUID projectId, UUID resourceId, LocalDate start, LocalDate end) {
    var entries = new ArrayList<ResourceHistogramEntry>();

    // Get max units per day for this resource
    double maxAvailable = 8.0; // default 8 hours
    try {
      Object maxResult = em.createNativeQuery(
              "SELECT max_units_per_day FROM resource.resources WHERE id = ?1")
          .setParameter(1, resourceId)
          .getSingleResult();
      if (maxResult != null) {
        maxAvailable = ((Number) maxResult).doubleValue();
      }
    } catch (Exception ignored) {}

    // Get all assignments for this resource in the date range
    try {
      List<Object> assignments = em.createNativeQuery(
              "SELECT planned_start_date, planned_finish_date, planned_units, actual_units " +
              "FROM resource.resource_assignments " +
              "WHERE project_id = ?1 AND resource_id = ?2 " +
              "AND planned_start_date <= ?4 AND planned_finish_date >= ?3 " +
              "ORDER BY planned_start_date")
          .setParameter(1, projectId)
          .setParameter(2, resourceId)
          .setParameter(3, start)
          .setParameter(4, end)
          .getResultList();

      // Build daily histogram by iterating through weeks (daily would be too many rows)
      LocalDate current = start;
      while (!current.isAfter(end)) {
        double plannedUnits = 0;
        double actualUnits = 0;

        for (Object row : assignments) {
          Object[] cols = (Object[]) row;
          LocalDate aStart = cols[0] != null ? LocalDate.parse(cols[0].toString()) : null;
          LocalDate aEnd = cols[1] != null ? LocalDate.parse(cols[1].toString()) : null;
          if (aStart != null && aEnd != null && !current.isBefore(aStart) && !current.isAfter(aEnd)) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(aStart, aEnd) + 1;
            if (days > 0) {
              plannedUnits += cols[2] != null ? ((Number) cols[2]).doubleValue() / days : 0;
              actualUnits += cols[3] != null ? ((Number) cols[3]).doubleValue() / days : 0;
            }
          }
        }

        entries.add(new ResourceHistogramEntry(
            current, plannedUnits, actualUnits, maxAvailable, plannedUnits > maxAvailable));
        current = current.plusDays(1);
      }
    } catch (Exception e) {
      // Return empty on failure
      log.warn("Failed to generate resource histogram: {}", e.getMessage());
    }

    return entries;
  }

  private String generateReportData(ReportType reportType, UUID projectId, Map<String, String> parameters) {
    return switch (reportType) {
      case TABULAR -> "{\"type\": \"TABULAR\", \"rows\": []}";
      case GRAPHICAL -> "{\"type\": \"GRAPHICAL\", \"data\": []}";
      case MATRIX -> "{\"type\": \"MATRIX\", \"data\": []}";
      case S_CURVE -> "{\"type\": \"S_CURVE\", \"dataPoints\": []}";
      case RESOURCE_HISTOGRAM -> "{\"type\": \"RESOURCE_HISTOGRAM\", \"entries\": []}";
      case CASH_FLOW -> "{\"type\": \"CASH_FLOW\", \"periods\": []}";
      case SCHEDULE_COMPARISON -> "{\"type\": \"SCHEDULE_COMPARISON\", \"variance\": []}";
    };
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> queryActivityData(UUID projectId) {
    try {
      List<Object> results = em.createNativeQuery(
              "SELECT code, name, status, planned_start_date, planned_finish_date, " +
              "original_duration, total_float, is_critical, remaining_duration " +
              "FROM activity.activities WHERE project_id = ?1 ORDER BY code")
          .setParameter(1, projectId)
          .getResultList();

      return results.stream().map(row -> {
        Object[] cols = (Object[]) row;
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("code", cols[0] != null ? cols[0].toString() : "");
        map.put("name", cols[1] != null ? cols[1].toString() : "");
        map.put("status", cols[2] != null ? cols[2].toString() : "");
        map.put("start", cols[3] != null ? cols[3].toString() : "");
        map.put("finish", cols[4] != null ? cols[4].toString() : "");
        map.put("duration", cols[5] != null ? ((Number) cols[5]).doubleValue() : 0.0);
        map.put("float", cols[6] != null ? ((Number) cols[6]).doubleValue() : 0.0);
        map.put("critical", cols[7] != null && (Boolean) cols[7]);
        map.put("percentComplete", cols[8] != null ? 100.0 - ((Number) cols[8]).doubleValue() : 0.0);
        return map;
      }).collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Failed to query activity data: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> queryCostData(UUID projectId) {
    try {
      List<Object> results = em.createNativeQuery(
              "SELECT name, budgeted_cost, actual_cost, remaining_cost, at_completion_cost " +
              "FROM cost.activity_expenses WHERE project_id = ?1 ORDER BY name")
          .setParameter(1, projectId)
          .getResultList();

      return results.stream().map(row -> {
        Object[] cols = (Object[]) row;
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("wbs", cols[0] != null ? cols[0].toString() : "");
        map.put("budget", cols[1] != null ? ((Number) cols[1]).doubleValue() : 0.0);
        map.put("actual", cols[2] != null ? ((Number) cols[2]).doubleValue() : 0.0);
        map.put("remaining", cols[3] != null ? ((Number) cols[3]).doubleValue() : 0.0);
        map.put("eac", cols[4] != null ? ((Number) cols[4]).doubleValue() : 0.0);
        return map;
      }).collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Failed to query cost data: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  private String getProjectName(UUID projectId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT name FROM project.projects WHERE id = ?1")
          .setParameter(1, projectId)
          .getSingleResult();
      return result != null ? result.toString() : "Project " + projectId;
    } catch (Exception e) {
      return "Project " + projectId;
    }
  }

  private byte[] generateExcelReport(ReportType reportType, UUID projectId) {
    return switch (reportType) {
      case TABULAR -> excelReportGenerator.generateActivityReport(projectId, queryActivityData(projectId));
      case RESOURCE_HISTOGRAM ->
          excelReportGenerator.generateResourceReport(projectId, new ArrayList<>());
      case CASH_FLOW -> excelReportGenerator.generateCostReport(projectId, queryCostData(projectId));
      default -> new byte[0];
    };
  }

  private byte[] generatePdfReport(ReportType reportType, UUID projectId) {
    String projectName = getProjectName(projectId);
    return switch (reportType) {
      case TABULAR -> {
        var data = queryActivityData(projectId);
        String html = buildActivityHtmlTable(data);
        yield pdfReportGenerator.generateActivityReport(projectId, projectName, html);
      }
      case RESOURCE_HISTOGRAM ->
          pdfReportGenerator.generateResourceReport(projectId, projectName, "<p>No resource data available</p>");
      case CASH_FLOW -> {
        var data = queryCostData(projectId);
        String html = buildCostHtmlTable(data);
        yield pdfReportGenerator.generateCostReport(projectId, projectName, html);
      }
      default -> new byte[0];
    };
  }

  private String buildActivityHtmlTable(List<Map<String, Object>> data) {
    if (data.isEmpty()) return "<p>No activities found</p>";
    var sb = new StringBuilder("<table><thead><tr>");
    sb.append("<th>Code</th><th>Name</th><th>Status</th><th>Start</th><th>Finish</th><th>Duration</th><th>Float</th><th>Critical</th>");
    sb.append("</tr></thead><tbody>");
    for (var row : data) {
      sb.append("<tr>");
      sb.append("<td>").append(row.getOrDefault("code", "")).append("</td>");
      sb.append("<td>").append(row.getOrDefault("name", "")).append("</td>");
      sb.append("<td>").append(row.getOrDefault("status", "")).append("</td>");
      sb.append("<td>").append(row.getOrDefault("start", "")).append("</td>");
      sb.append("<td>").append(row.getOrDefault("finish", "")).append("</td>");
      sb.append("<td>").append(row.getOrDefault("duration", 0.0)).append("</td>");
      sb.append("<td>").append(row.getOrDefault("float", 0.0)).append("</td>");
      sb.append("<td>").append(row.getOrDefault("critical", false)).append("</td>");
      sb.append("</tr>");
    }
    sb.append("</tbody></table>");
    return sb.toString();
  }

  private String buildCostHtmlTable(List<Map<String, Object>> data) {
    if (data.isEmpty()) return "<p>No cost data found</p>";
    var sb = new StringBuilder("<table><thead><tr>");
    sb.append("<th>WBS</th><th>Budget</th><th>Actual</th><th>Remaining</th><th>EAC</th>");
    sb.append("</tr></thead><tbody>");
    for (var row : data) {
      sb.append("<tr>");
      sb.append("<td>").append(row.getOrDefault("wbs", "")).append("</td>");
      sb.append("<td>").append(row.getOrDefault("budget", 0.0)).append("</td>");
      sb.append("<td>").append(row.getOrDefault("actual", 0.0)).append("</td>");
      sb.append("<td>").append(row.getOrDefault("remaining", 0.0)).append("</td>");
      sb.append("<td>").append(row.getOrDefault("eac", 0.0)).append("</td>");
      sb.append("</tr>");
    }
    sb.append("</tbody></table>");
    return sb.toString();
  }

  private String generateFileName(ReportType reportType, ReportFormat format) {
    String timestamp = String.valueOf(System.currentTimeMillis());
    String extension = format == ReportFormat.EXCEL ? ".xlsx" : ".pdf";
    return "report_" + reportType.toString().toLowerCase() + "_" + timestamp + extension;
  }

  // Delegate methods to ReportDataService
  @Transactional(readOnly = true)
  public com.bipros.reporting.application.dto.MonthlyProgressData getMonthlyProgress(
      UUID projectId, String period) {
    return reportDataService.getMonthlyProgress(projectId, period);
  }

  @Transactional(readOnly = true)
  public com.bipros.reporting.application.dto.EvmReportData getEvmReport(UUID projectId) {
    return reportDataService.getEvmReport(projectId);
  }

  @Transactional(readOnly = true)
  public List<com.bipros.reporting.application.dto.CashFlowEntry> getCashFlowReport(
      UUID projectId) {
    return reportDataService.getCashFlowReport(projectId);
  }

  @Transactional(readOnly = true)
  public com.bipros.reporting.application.dto.ContractStatusData getContractStatus(
      UUID projectId) {
    return reportDataService.getContractStatus(projectId);
  }

  @Transactional(readOnly = true)
  public com.bipros.reporting.application.dto.RiskRegisterData getRiskRegister(UUID projectId) {
    return reportDataService.getRiskRegister(projectId);
  }

  @Transactional(readOnly = true)
  public com.bipros.reporting.application.dto.ResourceUtilizationData getResourceUtilization(
      UUID projectId) {
    return reportDataService.getResourceUtilization(projectId);
  }

  @Transactional(readOnly = true)
  public com.bipros.reporting.application.dto.TrendAnalysisData getTrendAnalysis(
      UUID projectId, int months) {
    return reportDataService.getTrendAnalysis(projectId, months);
  }
}
