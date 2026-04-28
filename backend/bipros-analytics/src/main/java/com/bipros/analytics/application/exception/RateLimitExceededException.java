package com.bipros.analytics.application.exception;

/**
 * Thrown by {@code AnalyticsAssistantService} when a user has exceeded their
 * per-hour query budget. The handler maps this to HTTP 429 with a
 * {@code Retry-After} header equal to {@link #getRetryAfterSeconds()}.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded; retry in " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
