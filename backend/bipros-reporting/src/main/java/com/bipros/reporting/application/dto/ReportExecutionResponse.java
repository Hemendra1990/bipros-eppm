package com.bipros.reporting.application.dto;

import com.bipros.reporting.domain.model.ReportExecution;
import com.bipros.reporting.domain.model.ReportFormat;
import com.bipros.reporting.domain.model.ReportStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportExecutionResponse(
    UUID id,
    UUID reportDefinitionId,
    UUID projectId,
    ReportFormat format,
    ReportStatus status,
    String parameters,
    String resultData,
    String filePath,
    Instant executedAt,
    Instant completedAt,
    String errorMessage,
    Instant createdAt) {

  public static ReportExecutionResponse from(ReportExecution entity) {
    return new ReportExecutionResponse(
        entity.getId(),
        entity.getReportDefinitionId(),
        entity.getProjectId(),
        entity.getFormat(),
        entity.getStatus(),
        entity.getParameters(),
        entity.getResultData(),
        entity.getFilePath(),
        entity.getExecutedAt(),
        entity.getCompletedAt(),
        entity.getErrorMessage(),
        entity.getCreatedAt());
  }
}
