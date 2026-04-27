package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DailyActivityResourceOutput;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DailyActivityResourceOutputResponse(
    UUID id,
    UUID projectId,
    LocalDate outputDate,
    UUID activityId,
    UUID resourceId,
    BigDecimal qtyExecuted,
    String unit,
    Double hoursWorked,
    Double daysWorked,
    String remarks,
    Instant createdAt,
    Instant updatedAt,
    String createdBy,
    String updatedBy
) {
  public static DailyActivityResourceOutputResponse from(DailyActivityResourceOutput o) {
    return new DailyActivityResourceOutputResponse(
        o.getId(),
        o.getProjectId(),
        o.getOutputDate(),
        o.getActivityId(),
        o.getResourceId(),
        o.getQtyExecuted(),
        o.getUnit(),
        o.getHoursWorked(),
        o.getDaysWorked(),
        o.getRemarks(),
        o.getCreatedAt(),
        o.getUpdatedAt(),
        o.getCreatedBy(),
        o.getUpdatedBy());
  }
}
