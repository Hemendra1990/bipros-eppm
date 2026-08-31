package com.bipros.ai.agent.domain;

/** Lifecycle status of an {@link AgentPipelineRun}. PARTIAL = at least one agent FAILED but the pipeline finished. */
public enum PipelineRunStatus {
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED
}
