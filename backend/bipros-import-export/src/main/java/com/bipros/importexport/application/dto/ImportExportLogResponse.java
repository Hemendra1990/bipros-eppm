package com.bipros.importexport.application.dto;

import com.bipros.importexport.domain.model.ImportExportLog;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportExportLogResponse(
    UUID id,
    UUID jobId,
    String level,
    String message,
    String entityType,
    String entityId,
    Integer lineNumber,
    Instant createdAt) {

  public static ImportExportLogResponse from(ImportExportLog entity) {
    return new ImportExportLogResponse(
        entity.getId(),
        entity.getJobId(),
        entity.getLevel(),
        entity.getMessage(),
        entity.getEntityType(),
        entity.getEntityId(),
        entity.getLineNumber(),
        entity.getCreatedAt());
  }
}
