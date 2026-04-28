package com.bipros.analytics.infrastructure.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Sliding-by-the-hour per-user rate limiter for analytics queries. Backed by Redis;
 * the increment + expiry is atomic via a small Lua script ({@code rate_limit.lua}).
 *
 * <p>Key format: {@code <prefix>:<userId>:<hourEpoch>} so each hour rolls over to
 * a fresh bucket without needing to track sliding-window timestamps. Brief 2× burst
 * is possible at the hour boundary; acceptable for a 60-per-hour bound.
 *
 * <p>Bean is only created when {@code bipros.analytics.rate-limit.enabled=true}.
 * {@code AnalyticsAssistantService} injects this as {@code @Autowired(required=false)}
 * so disabling the flag keeps the orchestrator running without limits.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(name = "bipros.analytics.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AnalyticsRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final RateLimitProperties props;
    private final RedisScript<List> script;

    public AnalyticsRateLimiter(StringRedisTemplate redis, RateLimitProperties props) {
        this.redis = redis;
        this.props = props;
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/rate_limit.lua")));
        s.setResultType(List.class);
        this.script = s;
    }

    public Decision check(UUID userId) {
        if (userId == null) {
            // Unauthenticated requests should not normally reach here, but if they do
            // we fail open rather than blocking — the orchestrator's auth resolver will
            // reject them on its own terms.
            return new Decision(true, 0L, 0L);
        }
        long bucket = System.currentTimeMillis() / 1000L / WINDOW.getSeconds();
        String key = props.redisKeyPrefixOrDefault() + ":" + userId + ":" + bucket;
        int limit = props.queriesPerHourOrDefault();
        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redis.execute(
                    script,
                    List.of(key),
                    String.valueOf(limit),
                    String.valueOf(WINDOW.getSeconds()));
            if (result == null || result.size() < 3) {
                log.warn("ANALYTICS_RATE_LIMIT_UNEXPECTED_RESULT result={}", result);
                return new Decision(true, 0L, 0L); // fail open
            }
            boolean allowed = result.get(0) == 1L;
            long count = result.get(1);
            long ttl = Math.max(1L, result.get(2));
            return new Decision(allowed, count, ttl);
        } catch (Exception ex) {
            // Redis outage: fail open so the assistant stays available. The audit log still
            // captures the underlying request; ops can spot a rate-limit gap from monitoring.
            log.error("ANALYTICS_RATE_LIMIT_REDIS_FAIL key={} - failing open", key, ex);
            return new Decision(true, 0L, 0L);
        }
    }

    /** {@code allowed} = under limit; {@code count} = current count after this hit;
     *  {@code retryAfterSeconds} = seconds until the bucket TTL expires. */
    public record Decision(boolean allowed, long count, long retryAfterSeconds) {}
}
