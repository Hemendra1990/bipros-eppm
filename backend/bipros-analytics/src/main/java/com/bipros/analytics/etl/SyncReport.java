package com.bipros.analytics.etl;

import java.time.Instant;

/**
 * Outcome of one {@link SyncHandler#sync(Instant)} invocation.
 *
 * @param newWatermark next high-water mark; equal to {@code since} if nothing was pulled
 * @param rowsPulled   number of source rows fetched
 * @param rowsWritten  number of rows successfully inserted into ClickHouse
 * @param message      optional human note recorded on the watermark row
 */
public record SyncReport(
        Instant newWatermark,
        long rowsPulled,
        long rowsWritten,
        String message
) {
    public static SyncReport empty(Instant since) {
        return new SyncReport(since, 0, 0, null);
    }
}
