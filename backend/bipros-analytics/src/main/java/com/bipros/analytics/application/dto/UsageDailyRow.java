package com.bipros.analytics.application.dto;

import java.time.Instant;

/**
 * One row of the per-day, per-provider usage breakdown for a single user.
 * Populated by {@code AnalyticsAuditLogRepository.aggregateDailyByProvider}.
 */
public record UsageDailyRow(
        Instant day,
        String provider,
        Long tokensIn,
        Long tokensOut,
        Long costMicros,
        Long queryCount
) {}
