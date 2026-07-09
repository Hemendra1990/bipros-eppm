package com.bipros.ai.agent.domain;

/** Lifecycle status of a single {@link AgentRun}. */
public enum AgentRunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    /** Data snapshot hash matched the last successful run and the run was not forced. Zero LLM cost. */
    SKIPPED_NO_CHANGE
}
