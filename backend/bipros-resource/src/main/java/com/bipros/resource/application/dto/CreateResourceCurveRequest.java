package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateResourceCurveRequest(
    @NotBlank(message = "Name is required") String name,
    String description,
    @NotNull(message = "Curve data is required") List<Double> curveData) {}
