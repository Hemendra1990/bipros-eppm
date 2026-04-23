package com.bipros.project.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateDailyWeatherRequest(
    @NotNull LocalDate logDate,

    Double tempMaxC,
    Double tempMinC,
    Double rainfallMm,
    Double windKmh,
    String weatherCondition,
    Double workingHours,
    String remarks
) {}
