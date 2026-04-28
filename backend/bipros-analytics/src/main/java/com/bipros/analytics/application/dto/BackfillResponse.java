package com.bipros.analytics.application.dto;

import java.time.Instant;

/**
 * Result of a single-table backfill. Returned synchronously from
 * {@code POST /v1/admin/analytics/backfill}.
 */
public record BackfillResponse(
        String tableName,
        long rowsBefore,
        long rowsAfter,
        Instant startedAt,
        Instant finishedAt,
        long durationMillis,
        String status,
        String message
) {
}
