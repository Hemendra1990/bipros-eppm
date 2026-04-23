package com.bipros.reporting.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VariationOrderRow(
    UUID id,
    UUID contractId,
    String voNumber,
    String description,
    BigDecimal costImpactCrores,
    Integer timeImpactDays,
    String status,
    String approvedBy,
    LocalDate approvedDate) {}
