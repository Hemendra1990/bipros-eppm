package com.bipros.activity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateActivityStepRequest(
    @NotBlank(message = "Name is required")
    String name,

    String description,

    @Positive(message = "Weight must be positive")
    Double weight
) {}
