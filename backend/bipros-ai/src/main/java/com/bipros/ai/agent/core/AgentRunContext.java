package com.bipros.ai.agent.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable context threaded through a single agent run.
 *
 * @param projectId     the project under analysis; {@code null} for a portfolio-wide run
 * @param portfolio     true when this is a cross-project run (executive/notification agents)
 * @param triggerType   EVENT | SWEEP | MANUAL | PIPELINE | SUPERVISOR
 * @param triggerRef    optional reference to what triggered the run (event id, cron key, "user:{id}")
 * @param force         bypass the data-hash "skip if unchanged" optimisation
 * @param pipelineRunId parent pipeline run id ({@code null} for a standalone single-agent run)
 * @param requestedBy   the user who requested a manual run ({@code null} for system triggers)
 * @param now           run timestamp (injected so runs are deterministic/testable)
 */
public record AgentRunContext(
        UUID projectId,
        boolean portfolio,
        String triggerType,
        String triggerRef,
        boolean force,
        UUID pipelineRunId,
        UUID requestedBy,
        Instant now) {

    public static AgentRunContext manual(UUID projectId, UUID requestedBy) {
        return new AgentRunContext(projectId, projectId == null, "MANUAL", null, true, null, requestedBy, Instant.now());
    }

    public static AgentRunContext forPipeline(UUID projectId, boolean portfolio, String triggerType,
                                              String triggerRef, UUID pipelineRunId, Instant now) {
        return new AgentRunContext(projectId, portfolio, triggerType, triggerRef, false, pipelineRunId, null, now);
    }
}
