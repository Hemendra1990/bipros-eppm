package com.bipros.baseline.application.dto;

import com.bipros.baseline.domain.BaselineType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBaselineRequest(
    @NotBlank(message = "Name is required") String name,
    @NotNull(message = "Baseline type is required") BaselineType baselineType,
    String description) {}
