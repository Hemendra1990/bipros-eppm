package com.bipros.reporting.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractorLeagueRow(
    String contractorCode,
    String contractorName,
    long activeProjects,
    double avgPerformance,
    double avgSpi,
    double avgCpi,
    BigDecimal totalContractValueCrores,
    BigDecimal totalRaBillsCrores) {}
