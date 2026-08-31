package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.MaterialReturn;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMaterialReturnRequest(
    @NotNull LocalDate returnDate,
    @NotNull @Positive BigDecimal quantity,
    @NotNull MaterialReturn.ReturnCondition condition,
    UUID receivedByUserId,
    @Size(max = 500) String remarks
) {
}
