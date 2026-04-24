package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMaterialIssueRequest(
    @NotNull UUID materialId,
    @NotNull LocalDate issueDate,
    @NotNull @PositiveOrZero BigDecimal quantity,
    UUID issuedToUserId,
    UUID stretchId,
    UUID activityId,
    @PositiveOrZero BigDecimal wastageQuantity,
    @Size(max = 500) String remarks
) {
}
