package com.bipros.resource.application.dto;

import java.util.UUID;

public record EquipmentUtilizationSummary(
    UUID resourceId,
    String resourceName,
    Double totalOperatingHours,
    Double totalIdleHours,
    Double totalBreakdownHours,
    Double utilizationPercentage,
    Double totalAvailableHours
) {}
