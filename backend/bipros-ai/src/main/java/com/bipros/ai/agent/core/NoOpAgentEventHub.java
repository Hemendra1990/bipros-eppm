package com.bipros.ai.agent.core;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default {@link AgentEventHub} — discards events. Present so the framework works with no
 * streaming layer wired. The real SSE hub in {@code agent/stream} is annotated to win over this
 * ({@code @ConditionalOnMissingBean} lets a concrete hub replace it).
 */
@Component
@ConditionalOnMissingBean(AgentEventHub.class)
public class NoOpAgentEventHub implements AgentEventHub {

    @Override
    public void emit(AgentStreamEvent event) {
        // no-op
    }
}
