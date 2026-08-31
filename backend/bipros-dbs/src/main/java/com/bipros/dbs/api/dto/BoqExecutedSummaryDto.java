package com.bipros.dbs.api.dto;

import java.math.BigDecimal;

/**
 * Summary of BOQ execution over a period window: the count of distinct BOQ
 * items executed and the total qty_executed summed across approved DPRs that
 * have a BOQ item linked. Returned by {@code GET /v1/projects/{id}/dbs/boq-executed-summary}.
 */
public record BoqExecutedSummaryDto(
    long boqItemsExecuted,
    BigDecimal boqQtyExecuted,
    /** Stage 4: the BILLABLE subset of {@code boqQtyExecuted} — measurement-operation, pre-split
     *  and partition rows only. Equals {@code boqQtyExecuted} when no split line is involved. */
    BigDecimal boqBillableQty) {}
