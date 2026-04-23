package com.bipros.reporting.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CostVarianceRow(
    UUID wbsNodeId,
    String wbsCode,
    String wbsName,
    Integer level,
    BigDecimal budgetCrores,
    BigDecimal committedCrores,
    BigDecimal actualCrores,
    BigDecimal forecastCrores,
    BigDecimal varianceCrores,
    double variancePct) {}
