package com.bipros.common.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published by {@code DailyProgressReportService} after a DPR row is committed (CREATED,
 * UPDATED, or DELETED). Triggers BOQ qty sync (via {@code DprBoqSyncListener}) and analytics
 * ETL into {@code fact_dpr_logs} / {@code fact_activity_progress_daily}.
 *
 * <p>For UPDATED, {@code qtyExecuted} / {@code boqItemNo} are the new values; {@code oldQty}
 * / {@code oldBoqItemNo} hold the prior values so a listener can apply a delta. For DELETED,
 * the new values mirror the deleted row (so the listener can subtract its qty from BOQ); the
 * {@code dprId} still resolves to a (now-removed) row and the event carries enough state to
 * skip a follow-up read.
 */
public record DprSubmittedEvent(
    UUID projectId,
    UUID dprId,
    LocalDate reportDate,
    String activityName,
    String boqItemNo,
    BigDecimal qtyExecuted,
    String oldBoqItemNo,
    BigDecimal oldQty,
    DprMutationType eventType
) {
}
