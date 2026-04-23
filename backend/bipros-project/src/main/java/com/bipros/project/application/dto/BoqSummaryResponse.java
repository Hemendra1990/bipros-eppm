package com.bipros.project.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Project BOQ page response: the list of line items plus the grand-total row that mirrors
 * the Excel workbook's GRAND TOTAL.
 */
public record BoqSummaryResponse(
    List<BoqItemResponse> items,
    BigDecimal boqGrandTotal,
    BigDecimal budgetedGrandTotal,
    BigDecimal actualGrandTotal,
    BigDecimal grandCostVariance,
    BigDecimal grandCostVariancePercent,
    BigDecimal overallPercentComplete
) {}
