package com.bipros.resource.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ResourceUsageEntry(
    UUID resourceId,
    String resourceName,
    LocalDate date,
    Double plannedUnits,
    Double actualUnits) {}
