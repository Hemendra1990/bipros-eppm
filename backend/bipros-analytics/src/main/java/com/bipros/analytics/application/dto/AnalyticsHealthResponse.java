package com.bipros.analytics.application.dto;

import java.util.List;

/**
 * Single payload powering the /admin/analytics-health page. One round trip;
 * the page renders a KPI strip + four sections from this response.
 */
public record AnalyticsHealthResponse(
        int windowHours,
        List<WatermarkAge> watermarks,
        List<HourlyMetric> hourly,
        List<TopUserRow> topUsers,
        List<ErrorBucket> errors
) {}
