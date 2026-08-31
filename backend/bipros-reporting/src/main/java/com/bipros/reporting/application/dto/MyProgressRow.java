package com.bipros.reporting.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One supervised activity on the "My Progress" card (client ask, 2026-08-20):
 * quantity executed today / this week / this month / cumulative (approved DPRs
 * only, from ALL supervisors on the activity — physical progress, not authorship)
 * plus the activity's canonical percent complete. Zero-DPR activities are
 * included deliberately: a supervised activity with no filings is the row that
 * most needs the supervisor's attention.
 */
public record MyProgressRow(
    UUID activityId,
    String activityName,
    String boqItemNo,
    String unit,
    BigDecimal todayQty,
    BigDecimal weekQty,
    BigDecimal monthQty,
    BigDecimal cumulativeQty,
    Double percentComplete
) {}
