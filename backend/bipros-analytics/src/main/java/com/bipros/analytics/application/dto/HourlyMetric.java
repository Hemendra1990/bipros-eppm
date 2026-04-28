package com.bipros.analytics.application.dto;

import java.time.Instant;

/**
 * One hour-bucket of analytics-assistant traffic on the admin health page.
 * {@code total} = all requests in the bucket; {@code errors} = requests with
 * status outside (SUCCESS, REFUSED). Latency percentiles are over the bucket's
 * latency_ms values; null when the bucket has no rows with a recorded latency.
 */
public record HourlyMetric(
        Instant bucket,
        Long total,
        Long errors,
        Double latencyP50Ms,
        Double latencyP95Ms
) {}
