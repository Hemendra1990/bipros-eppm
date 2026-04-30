package com.bipros.common.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published by {@code DailyProgressReportService} after a DPR row is committed.
 * Triggers analytics ETL into {@code fact_dpr_logs} and {@code fact_activity_progress_daily}.
 */
public record DprSubmittedEvent(
    UUID projectId,
    UUID dprId,
    LocalDate reportDate,
    String activityName,
    BigDecimal qtyExecuted,
    BigDecimal cumulativeQty
) {
}
