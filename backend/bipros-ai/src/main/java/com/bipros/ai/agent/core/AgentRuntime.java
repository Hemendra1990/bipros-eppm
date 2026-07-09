package com.bipros.ai.agent.core;

import com.bipros.ai.agent.budget.LlmBudgetGuard;
import com.bipros.ai.agent.domain.AgentRunRepository;
import com.bipros.ai.agent.memory.AgentMemoryService;
import com.bipros.ai.insights.DataHashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Bundle of framework collaborators shared by every agent. Injected once into {@link AbstractAgent}
 * so concrete agents declare only their own domain-service dependencies.
 */
@Component
public record AgentRuntime(
        AgentNarrator narrator,
        AgentMemoryService memory,
        LlmBudgetGuard budget,
        AgentRunRepository runRepository,
        AgentEventHub eventHub,
        DataHashUtil dataHash,
        ObjectMapper objectMapper) {
}
