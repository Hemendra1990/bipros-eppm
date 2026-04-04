package com.bipros.activity.application.service;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ApplyActualsRequest(
    @NotNull(message = "Data date is required")
    LocalDate dataDate
) {}
