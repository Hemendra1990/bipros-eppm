package com.bipros.importexport.application.dto;

import com.bipros.importexport.domain.model.ImportExportDirection;
import com.bipros.importexport.domain.model.ImportExportFormat;
import com.bipros.importexport.domain.model.ImportExportJob;
import com.bipros.importexport.domain.model.ImportExportStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportExportJobResponse(
    UUID id,
    ImportExportFormat format,
    ImportExportDirection direction,
    UUID projectId,
    String fileName,
    String filePath,
    ImportExportStatus status,
    Integer totalRecords,
    Integer processedRecords,
    Integer errorCount,
    Instant startedAt,
    Instant completedAt,
    String errorLog,
    UUID importedProjectId,
    Instant createdAt) {

  public static ImportExportJobResponse from(ImportExportJob entity) {
    return new ImportExportJobResponse(
        entity.getId(),
        entity.getFormat(),
        entity.getDirection(),
        entity.getProjectId(),
        entity.getFileName(),
        entity.getFilePath(),
        entity.getStatus(),
        entity.getTotalRecords(),
        entity.getProcessedRecords(),
        entity.getErrorCount(),
        entity.getStartedAt(),
        entity.getCompletedAt(),
        entity.getErrorLog(),
        entity.getImportedProjectId(),
        entity.getCreatedAt());
  }
}
