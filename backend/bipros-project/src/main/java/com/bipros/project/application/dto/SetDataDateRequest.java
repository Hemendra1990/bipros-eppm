package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SetDataDateRequest(
    @NotNull(message = "dataDate is required")
    LocalDate dataDate
) {
}
