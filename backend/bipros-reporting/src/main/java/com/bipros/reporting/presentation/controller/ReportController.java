package com.bipros.reporting.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.reporting.application.dto.*;
import com.bipros.reporting.application.service.ReportService;
import com.bipros.reporting.domain.model.ReportFormat;
import com.bipros.reporting.domain.model.ReportType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;

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
}
