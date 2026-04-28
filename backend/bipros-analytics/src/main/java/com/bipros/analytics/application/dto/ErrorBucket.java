package com.bipros.analytics.application.dto;

import com.bipros.analytics.domain.model.AnalyticsAuditLog;

/**
 * One row of the error histogram on the admin health page. Grouped by
 * (status, error_kind) — e.g. (LLM_ERROR, "AUTH_RESOLVE") with a count.
 */
public record ErrorBucket(
        AnalyticsAuditLog.Status status,
        String errorKind,
        Long count
) {}
