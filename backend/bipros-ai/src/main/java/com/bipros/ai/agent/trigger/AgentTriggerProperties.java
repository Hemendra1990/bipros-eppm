package com.bipros.ai.agent.trigger;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Debounce/coalescing configuration for reactive pipeline triggers. Bound from
 * {@code bipros.agent.trigger} in application.yml.
 *
 * <p>A burst of source events for the same {@code (pipeline, project)} collapses into one queue row
 * whose {@code dueAt} is pushed to {@code now + quietWindow} on every event; {@code maxDueAt} caps
 * the total wait at {@code firstSeen + maxWindow} so a sustained burst still dispatches.
 */
@Component
@ConfigurationProperties(prefix = "bipros.agent.trigger")
@Getter
@Setter
public class AgentTriggerProperties {

    /** Quiet window: dispatch a coalesced trigger this many seconds after the last event. */
    private long quietWindowSeconds = 120;

    /** Hard cap (minutes) from the first event, guaranteeing dispatch under a sustained burst. */
    private long maxWindowMinutes = 10;

    /** Fixed delay (ms) between drain-job sweeps. Also referenced directly by the drain job's cron. */
    private long drainFixedDelayMs = 30_000;
}
