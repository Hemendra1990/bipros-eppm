package com.bipros.reporting.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectComplianceDto(
    PfmsBlock pfms,
    GstnBlock gstn,
    GemBlock gem,
    CpppBlock cppp,
    PariveshBlock parivesh,
    HseBlock hse,
    double overallScore) {

  public record PfmsBlock(boolean sanctionOk, LocalDate lastRelease, BigDecimal pendingAmount) {}

  public record GstnBlock(long contractorCount, long verifiedCount, long expiredCount) {}

  public record GemBlock(long linkedOrders, BigDecimal totalValueCrores) {}

  public record CpppBlock(long publishedTenders, long openBids) {}

  public record PariveshBlock(long clearancesObtained, long pendingClearances) {}

  public record HseBlock(long last30DaysIncidents, long openNCRs) {}
}
