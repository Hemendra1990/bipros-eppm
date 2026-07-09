package com.bipros.ai.agent.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-(scope, day) LLM token accounting for the budget guard. One row per project per day, plus a
 * row for the global scope keyed by the {@link #GLOBAL_SCOPE} sentinel project id.
 *
 * <p>{@code tokensReserved} counts optimistic per-run reservations not yet reconciled;
 * {@code tokensUsed} counts actuals posted after a run. The guard checks
 * {@code reserved + used + perRunCap <= cap} under a row lock (see {@code LlmBudgetGuard}).
 */
@Entity
@Table(schema = "ai", name = "agent_budget_usage",
        uniqueConstraints = @UniqueConstraint(name = "uq_agent_budget_scope_date", columnNames = {"project_id", "usage_date"}))
@Getter
@Setter
public class AgentBudgetUsage extends BaseEntity {

    /** Sentinel project id for the global (all-projects) daily budget row. */
    public static final UUID GLOBAL_SCOPE = new UUID(0L, 0L);

    @Column(name = "project_id", nullable = false)
    private UUID projectId = GLOBAL_SCOPE;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "tokens_reserved", nullable = false)
    private long tokensReserved = 0;

    @Column(name = "tokens_used", nullable = false)
    private long tokensUsed = 0;

    @Column(name = "run_count", nullable = false)
    private int runCount = 0;
}
