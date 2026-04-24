package com.bipros.resource.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateGoodsReceiptRequest(
    @NotNull UUID materialId,
    @NotNull LocalDate receivedDate,
    @NotNull @Positive BigDecimal quantity,
    @PositiveOrZero BigDecimal unitRate,
    UUID supplierOrganisationId,
    @Size(max = 50) String poNumber,
    @Size(max = 30) String vehicleNumber,
    UUID receivedByUserId,
    @PositiveOrZero BigDecimal acceptedQuantity,
    @PositiveOrZero BigDecimal rejectedQuantity,
    @Size(max = 500) String remarks
) {
}
