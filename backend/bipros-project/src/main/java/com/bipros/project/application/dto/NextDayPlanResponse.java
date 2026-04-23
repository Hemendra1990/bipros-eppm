package com.bipros.project.application.dto;

import com.bipros.project.domain.model.NextDayPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record NextDayPlanResponse(
    UUID id,
    UUID projectId,
    LocalDate reportDate,
    String nextDayActivity,
    Long chainageFromM,
    Long chainageToM,
    BigDecimal targetQty,
    String unit,
    String concerns,
    String actionBy,
    LocalDate dueDate
) {
  public static NextDayPlanResponse from(NextDayPlan p) {
    return new NextDayPlanResponse(
        p.getId(),
        p.getProjectId(),
        p.getReportDate(),
        p.getNextDayActivity(),
        p.getChainageFromM(),
        p.getChainageToM(),
        p.getTargetQty(),
        p.getUnit(),
        p.getConcerns(),
        p.getActionBy(),
        p.getDueDate()
    );
  }
}
