package com.bipros.analytics.application.dto;

import java.time.Instant;

/**
 * One ETL watermark on the admin health page. {@code stale=true} when the row
 * has not advanced in more than 30 minutes (configurable threshold).
 */
public record WatermarkAge(
        String tableName,
        Instant lastSyncedAt,
        Long ageSeconds,
        String lastRunStatus,
        boolean stale
) {}
