package com.bipros.resource.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProjectLabourDeploymentResponse(
    UUID id,
    UUID projectId,
    UUID designationId,
    Integer workerCount,
    BigDecimal actualDailyRate,
    BigDecimal effectiveRate,
    BigDecimal dailyCost,
    String notes,
    LabourDesignationResponse designation
) {}
