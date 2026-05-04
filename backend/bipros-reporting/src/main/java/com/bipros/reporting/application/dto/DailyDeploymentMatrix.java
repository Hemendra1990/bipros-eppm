package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * 31-day pivot of deployed hours per resource type, mirroring the "Daily Deployment" sheet of the
 * capacity-utilisation workbook. The schema does not currently track shift type per deployment
 * row — Day Shift / Night Shift sections are emitted as empty placeholders so the Excel layout
 * matches the template; the Total section is fully populated from {@code project.daily_resource_deployments}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyDeploymentMatrix(
    UUID projectId,
    YearMonth month,
    int daysInMonth,
    List<Section> sections
) {
  public record Section(String shift, List<Row> rows) {}

  public record Row(
      String resourceLabel,
      BigDecimal planHours,
      BigDecimal[] hoursPerDay,
      BigDecimal total
  ) {}
}
