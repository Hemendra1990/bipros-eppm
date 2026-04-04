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

  @Transactional
  public ReportDefinitionResponse createReportDefinition(CreateReportDefinitionRequest request) {
    var entity = new ReportDefinition();
    entity.setName(request.name());
    entity.setDescription(request.description());
    entity.setReportType(request.reportType());
    entity.setConfigJson(request.configJson());

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
  public List<SCurveDataPoint> generateSCurveData(
      UUID projectId, LocalDate start, LocalDate end, String interval) {
    // Placeholder implementation - generates sample S-curve data
    var dataPoints = new ArrayList<SCurveDataPoint>();

    LocalDate current = start;
    while (!current.isAfter(end)) {
      // In a real implementation, query activities/costs for the period
      // For now, generate sample data
      dataPoints.add(
          new SCurveDataPoint(
              current,
              java.math.BigDecimal.valueOf(Math.random() * 1000),
              java.math.BigDecimal.valueOf(Math.random() * 900),
              java.math.BigDecimal.valueOf(Math.random() * 950)));

      current =
          switch (interval) {
            case "WEEKLY" -> current.plusWeeks(1);
            case "MONTHLY" -> current.plusMonths(1);
            default -> current.plusDays(1);
          };
    }

    return dataPoints;
  }

  @Transactional(readOnly = true)
  public List<ResourceHistogramEntry> generateResourceHistogram(
      UUID projectId, UUID resourceId, LocalDate start, LocalDate end) {
    // Placeholder implementation - generates sample histogram data
    var entries = new ArrayList<ResourceHistogramEntry>();

    LocalDate current = start;
    while (!current.isAfter(end)) {
      entries.add(
          new ResourceHistogramEntry(
              current,
              Math.random() * 100,
              Math.random() * 80,
              100.0,
              Math.random() > 0.7));

      current = current.plusDays(1);
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

  private byte[] generateExcelReport(ReportType reportType, UUID projectId) {
    return switch (reportType) {
      case TABULAR -> excelReportGenerator.generateActivityReport(projectId, new ArrayList<>());
      case RESOURCE_HISTOGRAM ->
          excelReportGenerator.generateResourceReport(projectId, new ArrayList<>());
      case CASH_FLOW -> excelReportGenerator.generateCostReport(projectId, new ArrayList<>());
      default -> new byte[0];
    };
  }

  private byte[] generatePdfReport(ReportType reportType, UUID projectId) {
    String projectName = "Project " + projectId;
    return switch (reportType) {
      case TABULAR -> pdfReportGenerator.generateActivityReport(projectId, projectName, "<p>No data</p>");
      case RESOURCE_HISTOGRAM ->
          pdfReportGenerator.generateResourceReport(projectId, projectName, "<p>No data</p>");
      case CASH_FLOW -> pdfReportGenerator.generateCostReport(projectId, projectName, "<p>No data</p>");
      default -> new byte[0];
    };
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
}
