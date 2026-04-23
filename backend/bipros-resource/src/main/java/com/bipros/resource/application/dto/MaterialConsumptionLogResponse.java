package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.MaterialConsumptionLog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaterialConsumptionLogResponse(
    UUID id,
    UUID projectId,
    LocalDate logDate,
    UUID resourceId,
    String materialName,
    String unit,
    BigDecimal openingStock,
    BigDecimal received,
    BigDecimal consumed,
    BigDecimal closingStock,
    BigDecimal wastagePercent,
    String issuedBy,
    String receivedBy,
    UUID wbsNodeId,
    String remarks,
    Instant createdAt,
    String createdBy
) {
  public static MaterialConsumptionLogResponse from(MaterialConsumptionLog entity) {
    return new MaterialConsumptionLogResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getLogDate(),
        entity.getResourceId(),
        entity.getMaterialName(),
        entity.getUnit(),
        entity.getOpeningStock(),
        entity.getReceived(),
        entity.getConsumed(),
        entity.getClosingStock(),
        entity.getWastagePercent(),
        entity.getIssuedBy(),
        entity.getReceivedBy(),
        entity.getWbsNodeId(),
        entity.getRemarks(),
        entity.getCreatedAt(),
        entity.getCreatedBy());
  }
}
