package com.bipros.analytics.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-user rate-limit settings for the analytics assistant. Bound from
 * {@code bipros.analytics.rate-limit.*} in application.yml.
 *
 * <p>{@code enabled=false} disables the limiter entirely (the bean is not created;
 * see {@link AnalyticsRateLimiter}).
 */
@ConfigurationProperties(prefix = "bipros.analytics.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Integer queriesPerHour,
        String redisKeyPrefix
) {
    public int queriesPerHourOrDefault() {
        return queriesPerHour == null || queriesPerHour <= 0 ? 60 : queriesPerHour;
    }

    public String redisKeyPrefixOrDefault() {
        return redisKeyPrefix == null || redisKeyPrefix.isBlank() ? "analytics:rl" : redisKeyPrefix;
    }
}
