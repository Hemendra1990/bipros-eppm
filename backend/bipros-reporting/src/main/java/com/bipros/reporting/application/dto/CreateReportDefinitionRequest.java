package com.bipros.reporting.application.dto;

import com.bipros.reporting.domain.model.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReportDefinitionRequest(
    @NotBlank String name,
    String description,
    @NotNull ReportType reportType,
    String configJson) {}
