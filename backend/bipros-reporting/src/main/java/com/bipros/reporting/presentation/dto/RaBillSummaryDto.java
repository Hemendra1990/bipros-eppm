package com.bipros.reporting.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RaBillSummaryDto(
    BigDecimal totalSubmittedCrores,
    BigDecimal pendingApprovalCrores,
    BigDecimal approvedCrores,
    BigDecimal paidCrores,
    BigDecimal rejectedCrores,
    BigDecimal retentionHeldCrores,
    BigDecimal ldAppliedCrores,
    List<BillRow> bills) {

  public record BillRow(
      UUID id,
      String billNumber,
      LocalDate billPeriodFrom,
      LocalDate billPeriodTo,
      String status,
      BigDecimal grossAmount,
      BigDecimal netAmount,
      LocalDate submittedDate,
      LocalDate approvedDate,
      LocalDate paidDate) {}
}
