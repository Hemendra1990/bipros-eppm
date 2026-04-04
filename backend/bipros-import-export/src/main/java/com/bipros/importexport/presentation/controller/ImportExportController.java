package com.bipros.importexport.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.importexport.application.dto.ExportProjectRequest;
import com.bipros.importexport.application.dto.ImportExportJobResponse;
import com.bipros.importexport.application.dto.ImportExportLogResponse;
import com.bipros.importexport.application.service.ImportExportService;
import com.bipros.importexport.domain.model.ImportExportFormat;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/import-export")
@RequiredArgsConstructor
public class ImportExportController {

  private final ImportExportService importExportService;

  @PostMapping("/export")
  public ApiResponse<ImportExportJobResponse> exportProject(
      @Valid @RequestBody ExportProjectRequest request) throws Exception {
    return ApiResponse.ok(
        importExportService.exportProject(request.projectId(), request.format()));
  }

  @PostMapping("/import")
  public ApiResponse<ImportExportJobResponse> importProject(
      @RequestParam("file") MultipartFile file,
      @RequestParam ImportExportFormat format) throws Exception {
    return ApiResponse.ok(importExportService.importProject(file, format));
  }

  @GetMapping("/jobs")
  public ApiResponse<List<ImportExportJobResponse>> listJobs() {
    return ApiResponse.ok(importExportService.listJobs());
  }

  @GetMapping("/jobs/{jobId}")
  public ApiResponse<ImportExportJobResponse> getJobStatus(@PathVariable UUID jobId) {
    return ApiResponse.ok(importExportService.getJobStatus(jobId));
  }

  @GetMapping("/jobs/{jobId}/logs")
  public ApiResponse<List<ImportExportLogResponse>> getJobLogs(@PathVariable UUID jobId) {
    return ApiResponse.ok(importExportService.getJobLogs(jobId));
  }

  @GetMapping("/jobs/{jobId}/download")
  public ApiResponse<String> downloadExportedFile(@PathVariable UUID jobId) {
    var job = importExportService.getJobStatus(jobId);
    return ApiResponse.ok(job.filePath());
  }
}
