package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DailyProgressReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DailyProgressReportResponse(
    UUID id,
    UUID projectId,
    LocalDate reportDate,
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
  public static DailyProgressReportResponse from(DailyProgressReport r) {
    return new DailyProgressReportResponse(
        r.getId(),
        r.getProjectId(),
        r.getReportDate(),
        r.getSupervisorName(),
        r.getChainageFromM(),
        r.getChainageToM(),
        r.getActivityName(),
        r.getWbsNodeId(),
        r.getBoqItemNo(),
        r.getUnit(),
        r.getQtyExecuted(),
        r.getCumulativeQty(),
        r.getWeatherCondition(),
        r.getRemarks()
    );
  }
}
