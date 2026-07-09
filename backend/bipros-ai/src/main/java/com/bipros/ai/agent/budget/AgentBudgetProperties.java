package com.bipros.ai.agent.budget;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM token caps for agent runs. Configure in application.yml under {@code bipros.agent.budget}.
 * When a reservation is denied, the run proceeds deterministically with templated narratives —
 * monitoring never goes dark.
 */
@Component
@ConfigurationProperties(prefix = "bipros.agent.budget")
@Getter
@Setter
public class AgentBudgetProperties {

    /** Optimistic tokens reserved per agent run before narration. */
    private long perRunTokens = 8000;

    /** Daily cap per project across all agents. */
    private long perProjectDailyTokens = 150_000;

    /** Daily cap across all projects. */
    private long globalDailyTokens = 2_000_000;

    /** Cap per supervisor investigation (ReAct loop). */
    private long supervisorPerInvestigationTokens = 30_000;
}
