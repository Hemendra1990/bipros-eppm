package com.bipros.ai.agent.domain;

/** Why (if at all) the LLM narration step was skipped for a run — recorded on {@link AgentRun}. */
public enum LlmSkipReason {
    /** Narration ran normally. */
    NONE,
    /** Data hash unchanged — whole run short-circuited before narration. */
    NO_CHANGE,
    /** Budget guard denied the reservation — run used templated narratives. */
    BUDGET,
    /** LLM call or parse failed after retry — run fell back to templated narratives. */
    LLM_ERROR,
    /** No LLM provider configured — run used templated narratives. */
    NOT_CONFIGURED
}
