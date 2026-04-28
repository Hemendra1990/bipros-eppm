package com.bipros.analytics.etl.watermark;

import java.time.Instant;

/**
 * One row of {@code etl_watermarks}.
 *
 * @param tableName      ClickHouse fact/dim name
 * @param lastSyncedAt   {@code max(updated_at)} of source rows that have been ingested
 * @param lastRunAt      timestamp of the last orchestrator touch, success or fail
 * @param status         SUCCESS, FAILED, PARTIAL
 * @param message        optional human note
 */
public record Watermark(
        String tableName,
        Instant lastSyncedAt,
        Instant lastRunAt,
        String status,
        String message
) {
    public static Watermark seed(String tableName) {
        return new Watermark(tableName, Instant.EPOCH, null, "SUCCESS", null);
    }
}
