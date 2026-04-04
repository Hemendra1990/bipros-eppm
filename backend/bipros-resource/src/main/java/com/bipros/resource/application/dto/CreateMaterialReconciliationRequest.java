package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMaterialReconciliationRequest(
    @NotNull UUID resourceId,
    @NotNull UUID projectId,
    UUID wbsNodeId,
    @NotNull String period,
    @NotNull Double openingBalance,
    @NotNull Double received,
    @NotNull Double consumed,
    Double wastage,
    String unit,
    String remarks
) {}
