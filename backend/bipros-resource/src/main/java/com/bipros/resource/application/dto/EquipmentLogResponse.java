package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.EquipmentLog;
import com.bipros.resource.domain.model.EquipmentStatus;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record EquipmentLogResponse(
    UUID id,
    UUID resourceId,
    UUID projectId,
    LocalDate logDate,
    String deploymentSite,
    Double operatingHours,
    Double idleHours,
    Double breakdownHours,
    Double fuelConsumed,
    String operatorName,
    String remarks,
    EquipmentStatus status,
    Instant createdAt,
    String createdBy
) {
  public static EquipmentLogResponse from(EquipmentLog entity) {
    return new EquipmentLogResponse(
        entity.getId(),
        entity.getResourceId(),
        entity.getProjectId(),
        entity.getLogDate(),
        entity.getDeploymentSite(),
        entity.getOperatingHours(),
        entity.getIdleHours(),
        entity.getBreakdownHours(),
        entity.getFuelConsumed(),
        entity.getOperatorName(),
        entity.getRemarks(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getCreatedBy());
  }
}
