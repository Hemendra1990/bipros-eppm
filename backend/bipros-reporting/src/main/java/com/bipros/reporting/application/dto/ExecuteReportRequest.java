package com.bipros.reporting.application.dto;

import com.bipros.reporting.domain.model.ReportFormat;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record ExecuteReportRequest(
    @NotNull UUID reportDefinitionId,
    UUID projectId,
    @NotNull ReportFormat format,
    Map<String, String> parameters) {}
