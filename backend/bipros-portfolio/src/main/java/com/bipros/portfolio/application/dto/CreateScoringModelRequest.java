package com.bipros.portfolio.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateScoringModelRequest(
    @NotBlank(message = "Model name is required") String name,
    String description) {}
