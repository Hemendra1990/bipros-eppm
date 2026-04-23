package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DailyWeather;

import java.time.LocalDate;
import java.util.UUID;

public record DailyWeatherResponse(
    UUID id,
    UUID projectId,
    LocalDate logDate,
    Double tempMaxC,
    Double tempMinC,
    Double rainfallMm,
    Double windKmh,
    String weatherCondition,
    Double workingHours,
    String remarks
) {
  public static DailyWeatherResponse from(DailyWeather w) {
    return new DailyWeatherResponse(
        w.getId(),
        w.getProjectId(),
        w.getLogDate(),
        w.getTempMaxC(),
        w.getTempMinC(),
        w.getRainfallMm(),
        w.getWindKmh(),
        w.getWeatherCondition(),
        w.getWorkingHours(),
        w.getRemarks()
    );
  }
}
