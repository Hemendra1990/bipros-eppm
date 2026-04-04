package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateProjectRequest(
    @NotBlank(message = "Code is required")
    @Size(max = 20, message = "Code must not exceed 20 characters")
    String code,

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    String name,

    String description,

    @NotNull(message = "EPS node ID is required")
    UUID epsNodeId,

    UUID obsNodeId,

    LocalDate plannedStartDate,

    LocalDate plannedFinishDate,

    Integer priority
) {
}
