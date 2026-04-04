package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.EquipmentStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateEquipmentLogRequest(
    @NotNull UUID resourceId,
    @NotNull UUID projectId,
    @NotNull LocalDate logDate,
    String deploymentSite,
    Double operatingHours,
    Double idleHours,
    Double breakdownHours,
    Double fuelConsumed,
    String operatorName,
    String remarks,
    EquipmentStatus status
) {}
