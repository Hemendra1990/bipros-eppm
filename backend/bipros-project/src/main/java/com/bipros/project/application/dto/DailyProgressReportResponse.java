package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DailyProgressReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DailyProgressReportResponse(
    UUID id,
    UUID projectId,
    LocalDate reportDate,
    UUID supervisorResourceId,
    String supervisorName,
    Long chainageFromM,
    Long chainageToM,
    String activityName,
    UUID wbsNodeId,
    String boqItemNo,
    String unit,
    BigDecimal qtyExecuted,
    BigDecimal cumulativeQty,
    String weatherCondition,
    String remarks
) {
  /**
   * Convenience constructor for the legacy call sites (audit logging) that don't have a
   * computed cumulative on hand. Sets cumulativeQty to the row's qtyExecuted as a placeholder
   * — the list endpoint always computes the real cumulative via the service.
   */
  public static DailyProgressReportResponse from(DailyProgressReport r) {
    return from(r, r.getQtyExecuted());
  }

  public static DailyProgressReportResponse from(DailyProgressReport r, BigDecimal cumulativeQty) {
    return new DailyProgressReportResponse(
        r.getId(),
        r.getProjectId(),
        r.getReportDate(),
        r.getSupervisorResourceId(),
        r.getSupervisorName(),
        r.getChainageFromM(),
        r.getChainageToM(),
        r.getActivityName(),
        r.getWbsNodeId(),
        r.getBoqItemNo(),
        r.getUnit(),
        r.getQtyExecuted(),
        cumulativeQty,
        r.getWeatherCondition(),
        r.getRemarks()
    );
  }
}
