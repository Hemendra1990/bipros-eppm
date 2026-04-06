package com.bipros.resource.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UtilizationProfileEntry(
        LocalDate date,
        UUID resourceId,
        String resourceName,
        double demand,
        double capacity,
        double utilization
) {}
