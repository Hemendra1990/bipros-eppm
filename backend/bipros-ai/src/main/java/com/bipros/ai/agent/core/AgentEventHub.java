package com.bipros.ai.agent.core;

/**
 * Sink for {@link AgentStreamEvent}s. The default {@link NoOpAgentEventHub} discards them; the
 * SSE-backed implementation in {@code agent/stream} (Track A) replaces it via
 * {@code @ConditionalOnMissingBean} and fans events out to per-project browser subscribers.
 *
 * <p>Decoupled as an interface in {@code core} so {@code AbstractAgent} and the pipeline runner
 * (Phase 0 / early Phase 1) can emit without depending on the streaming machinery.
 * Implementations must never throw — emitting is best-effort.
 */
public interface AgentEventHub {

    void emit(AgentStreamEvent event);
}
