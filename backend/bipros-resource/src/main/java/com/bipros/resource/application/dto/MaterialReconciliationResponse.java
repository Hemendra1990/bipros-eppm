package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.MaterialReconciliation;

import java.time.Instant;
import java.util.UUID;

public record MaterialReconciliationResponse(
    UUID id,
    UUID resourceId,
    UUID projectId,
    UUID wbsNodeId,
    String period,
    Double openingBalance,
    Double received,
    Double consumed,
    Double wastage,
    Double closingBalance,
    String unit,
    String remarks,
    Instant createdAt,
    String createdBy
) {
  public static MaterialReconciliationResponse from(MaterialReconciliation entity) {
    return new MaterialReconciliationResponse(
        entity.getId(),
        entity.getResourceId(),
        entity.getProjectId(),
        entity.getWbsNodeId(),
        entity.getPeriod(),
        entity.getOpeningBalance(),
        entity.getReceived(),
        entity.getConsumed(),
        entity.getWastage(),
        entity.getClosingBalance(),
        entity.getUnit(),
        entity.getRemarks(),
        entity.getCreatedAt(),
        entity.getCreatedBy());
  }
}
