package com.bipros.ai.agent.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * A lifecycle event emitted during an agent run, fanned out to the frontend "agents working"
 * feed over SSE. Deliberately lightweight and fire-and-forget — losing one is harmless.
 *
 * @param type          run_started | gathering | narrating | finding | run_finished
 * @param projectId     project scope (nullable for portfolio runs) — used to route to subscribers
 * @param agentKey      which agent
 * @param runId         the agent run id
 * @param pipelineRunId parent pipeline run (nullable)
 * @param payload       small type-specific JSON blob (status, findingsCount, a finding summary, …)
 * @param at            emission time
 */
public record AgentStreamEvent(
        String type,
        UUID projectId,
        String agentKey,
        UUID runId,
        UUID pipelineRunId,
        JsonNode payload,
        Instant at) {

    public static final String RUN_STARTED = "run_started";
    public static final String GATHERING = "gathering";
    public static final String NARRATING = "narrating";
    public static final String FINDING = "finding";
    public static final String RUN_FINISHED = "run_finished";
}
