package com.bipros.importexport.application.dto;

import com.bipros.importexport.domain.model.ImportExportFormat;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExportProjectRequest(
    @NotNull UUID projectId,
    @NotNull ImportExportFormat format) {}
