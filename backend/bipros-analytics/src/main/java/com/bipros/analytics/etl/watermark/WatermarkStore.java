package com.bipros.analytics.etl.watermark;

import com.bipros.analytics.etl.SyncReport;

public interface WatermarkStore {

    /**
     * Returns the watermark row for {@code tableName}. Never {@code null} — when no row exists,
     * {@link Watermark#seed(String)} is returned so callers can treat first-run identically.
     */
    Watermark read(String tableName);

    /** Records a successful run, advancing {@code last_synced_at} to the report's new watermark. */
    void writeSuccess(String tableName, SyncReport report);

    /** Records a failed run, leaving {@code last_synced_at} at its prior value. */
    void writeFailure(String tableName, String message);

    /**
     * Resets a table's watermark to epoch — used by the backfill flow before re-pulling all
     * source rows.
     */
    void reset(String tableName);
}
