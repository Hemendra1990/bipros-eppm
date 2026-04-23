package com.bipros.project.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyCostReportResponse(
    LocalDate from,
    LocalDate to,
    List<DailyCostReportRow> rows,
    BigDecimal periodBudgetedCost,
    BigDecimal periodActualCost,
    BigDecimal periodVariance,
    BigDecimal periodVariancePercent
) {}
