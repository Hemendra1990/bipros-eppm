package com.bipros.ai.agent.pipeline;

import com.bipros.ai.agent.core.AbstractAgent;
import com.bipros.ai.agent.core.Agent;
import com.bipros.ai.agent.core.AgentRegistry;
import com.bipros.ai.agent.core.AgentRunContext;
import com.bipros.ai.agent.domain.AgentRun;
import com.bipros.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Runs a single agent. Phase 0 provides the synchronous path used by the manual run endpoint and
 * tests; Track A layers async pipeline orchestration ({@code AgentPipelineRunner}) on top of the
 * same {@link AbstractAgent#run} entry point.
 */
@Service
@RequiredArgsConstructor
public class AgentRunService {

    private final AgentRegistry registry;

    /** Execute one agent synchronously and return its persisted {@link AgentRun}. */
    public AgentRun runSingle(String agentKey, AgentRunContext ctx) {
        Agent agent = registry.get(agentKey);
        if (!(agent instanceof AbstractAgent runnable)) {
            throw new BusinessRuleException("AGENT_NOT_RUNNABLE",
                    "Agent '" + agentKey + "' does not support direct execution");
        }
        return runnable.run(ctx);
    }
}
