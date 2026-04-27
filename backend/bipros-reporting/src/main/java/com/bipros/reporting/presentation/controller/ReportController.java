package com.bipros.reporting.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.reporting.application.dto.*;
import com.bipros.reporting.application.service.CapacityUtilizationReportService;
import com.bipros.reporting.application.service.ReportService;
import com.bipros.reporting.domain.model.ReportFormat;
import com.bipros.reporting.domain.model.ReportType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'VIEWER')")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;
  private final CapacityUtilizationReportService capacityUtilizationReportService;

  /** Alias for {@code /reports/definitions}. Dashboards and links that expect the bare
   * {@code /v1/reports} collection endpoint land here instead of receiving a 404. */
  @GetMapping
  public ApiResponse<List<ReportDefinitionResponse>> listReports(
      @RequestParam(required = false) ReportType type) {
    return ApiResponse.ok(reportService.listReportDefinitions(type));
  }

  @PostMapping("/definitions")
  public ApiResponse<ReportDefinitionResponse> createReportDefinition(
      @Valid @RequestBody CreateReportDefinitionRequest request) {
    return ApiResponse.ok(reportService.createReportDefinition(request));
  }

  @GetMapping("/definitions")
  public ApiResponse<List<ReportDefinitionResponse>> listReportDefinitions(
      @RequestParam(required = false) ReportType type) {
    return ApiResponse.ok(reportService.listReportDefinitions(type));
  }

  @GetMapping("/definitions/{id}")
  public ApiResponse<ReportDefinitionResponse> getReportDefinition(@PathVariable UUID id) {
    return ApiResponse.ok(reportService.getReportDefinition(id));
  }

  @DeleteMapping("/definitions/{id}")
  public ApiResponse<Void> deleteReportDefinition(@PathVariable UUID id) {
    reportService.deleteReportDefinition(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/execute")
  public ApiResponse<ReportExecutionResponse> executeReport(
      @Valid @RequestBody ExecuteReportRequest request) {
    return ApiResponse.ok(
        reportService.executeReport(
            request.reportDefinitionId(),
            request.projectId(),
            request.format(),
            request.parameters()));
  }

  @GetMapping("/executions/{id}")
  public ApiResponse<ReportExecutionResponse> getExecution(@PathVariable UUID id) {
    return ApiResponse.ok(reportService.getExecution(id));
  }

  @GetMapping("/executions")
  public ApiResponse<List<ReportExecutionResponse>> listExecutions(
      @RequestParam UUID projectId) {
    return ApiResponse.ok(reportService.listExecutions(projectId));
  }

  @GetMapping("/projects/{projectId}/s-curve")
  public ApiResponse<List<SCurveDataPoint>> getSCurveData(
      @PathVariable UUID projectId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(defaultValue = "DAILY") String interval) {
    return ApiResponse.ok(reportService.generateSCurveData(projectId, start, end, interval));
  }

  @GetMapping("/projects/{projectId}/resource-histogram")
  public ApiResponse<List<ResourceHistogramEntry>> getResourceHistogram(
      @PathVariable UUID projectId,
      @RequestParam UUID resourceId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
    return ApiResponse.ok(reportService.generateResourceHistogram(projectId, resourceId, start, end));
  }

  @GetMapping("/executions/{id}/download")
  public ResponseEntity<byte[]> downloadReportExecution(@PathVariable UUID id) {
    var execution = reportService.getExecution(id);

    MediaType contentType =
        execution.format() == ReportFormat.EXCEL
            ? MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            : MediaType.APPLICATION_PDF;

    String fileName = execution.filePath() != null ? execution.filePath() : "report.bin";

    return ResponseEntity.ok()
        .contentType(contentType)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .body(new byte[0]); // Placeholder: actual bytes would come from file storage
  }

  @GetMapping("/monthly-progress")
  public ApiResponse<MonthlyProgressData> getMonthlyProgress(
      @RequestParam UUID projectId,
      @RequestParam String period) {
    return ApiResponse.ok(reportService.getMonthlyProgress(projectId, period));
  }

  @GetMapping("/evm")
  public ApiResponse<EvmReportData> getEvmReport(@RequestParam UUID projectId) {
    return ApiResponse.ok(reportService.getEvmReport(projectId));
  }

  @GetMapping("/cash-flow")
  public ApiResponse<List<CashFlowEntry>> getCashFlowReport(@RequestParam UUID projectId) {
    return ApiResponse.ok(reportService.getCashFlowReport(projectId));
  }

  @GetMapping("/contract-status")
  public ApiResponse<ContractStatusData> getContractStatus(@RequestParam UUID projectId) {
    return ApiResponse.ok(reportService.getContractStatus(projectId));
  }

  @GetMapping("/risk-register")
  public ApiResponse<RiskRegisterData> getRiskRegister(@RequestParam UUID projectId) {
    return ApiResponse.ok(reportService.getRiskRegister(projectId));
  }

  @GetMapping("/resource-utilization")
  public ApiResponse<ResourceUtilizationData> getResourceUtilization(
      @RequestParam UUID projectId) {
    return ApiResponse.ok(reportService.getResourceUtilization(projectId));
  }

  @GetMapping("/capacity-utilization")
  public ApiResponse<CapacityUtilizationReport> getCapacityUtilization(
      @RequestParam UUID projectId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
      @RequestParam(required = false, defaultValue = "RESOURCE_TYPE") String groupBy,
      @RequestParam(required = false) String normType) {
    return ApiResponse.ok(
        capacityUtilizationReportService.build(projectId, fromDate, toDate, groupBy, normType));
  }

  @GetMapping("/trend-analysis")
  @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'COST_ENGINEER')")
  public ApiResponse<TrendAnalysisData> getTrendAnalysis(
      @RequestParam UUID projectId,
      @RequestParam(defaultValue = "6") int months) {
    return ApiResponse.ok(reportService.getTrendAnalysis(projectId, months));
  }
}
