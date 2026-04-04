package com.bipros.reporting.application.dto;

import com.bipros.reporting.domain.model.ReportDefinition;
import com.bipros.reporting.domain.model.ReportType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportDefinitionResponse(
    UUID id,
    String name,
    String description,
    ReportType reportType,
    Boolean isBuiltIn,
    UUID createdByUserId,
    String configJson,
    Instant createdAt,
    Instant updatedAt) {

  public static ReportDefinitionResponse from(ReportDefinition entity) {
    return new ReportDefinitionResponse(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.getReportType(),
        entity.getIsBuiltIn(),
        entity.getCreatedByUserId(),
        entity.getConfigJson(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
