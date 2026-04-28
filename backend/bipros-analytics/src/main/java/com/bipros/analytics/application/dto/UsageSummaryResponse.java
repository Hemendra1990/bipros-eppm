package com.bipros.analytics.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Per-user usage summary returned by {@code GET /v1/llm-providers/me/usage}.
 * Drives the Usage tab on /settings/llm-providers.
 */
public record UsageSummaryResponse(
        Instant from,
        Instant to,
        List<UsageDailyRow> daily,
        UsageTotals totals
) {
    public record UsageTotals(
            Long tokensIn,
            Long tokensOut,
            Long costMicros,
            Long queryCount
    ) {}
}
