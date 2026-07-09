package com.bipros.ai.agent.core;

/**
 * A specialized project-intelligence agent. Each implementation is a Spring bean auto-registered
 * in {@link AgentRegistry} by its {@link #key()}.
 *
 * <p>Deterministic-first contract: {@link #gather} does ALL data access and computes candidate
 * findings using domain services and rule thresholds — <b>no LLM call happens here</b>. The single
 * schema-strict LLM narration call is orchestrated later by {@code AbstractAgent} and only rewords
 * / ranks the candidates {@code gather} produced. This keeps monitoring cheap, testable, and
 * resilient when the LLM is down or over budget.
 */
public interface Agent {

    /** Stable machine key, e.g. {@code "planning_intelligence"}. Unique across all agents. */
    String key();

    /** Human display name, e.g. {@code "Planning Intelligence"}. */
    String displayName();

    /** True if this agent can run without a project (cross-project) — executive/notification agents. */
    boolean supportsPortfolio();

    /** Deterministic data gather + candidate computation. No LLM. Must not throw for a normal empty result. */
    GatherResult gather(AgentRunContext ctx);
}
