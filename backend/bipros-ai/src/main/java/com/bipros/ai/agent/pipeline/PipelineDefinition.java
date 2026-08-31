package com.bipros.ai.agent.pipeline;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Definition of a multi-stage agent pipeline. A pipeline is an ordered list of {@code stages};
 * every agent in a stage runs in parallel, and stages run strictly one after another (so a later
 * stage may read the findings a prior stage wrote to agent-memory).
 *
 * @param key    stable pipeline key (see {@link AgentPipelines})
 * @param stages ordered stages; each stage is the set of agent keys to run in parallel
 */
public record PipelineDefinition(String key, List<Set<String>> stages) {

    /** Flattened, de-duplicated set of every agent key referenced across all stages (stage order preserved). */
    public Set<String> allAgentKeys() {
        Set<String> all = new LinkedHashSet<>();
        for (Set<String> stage : stages) {
            all.addAll(stage);
        }
        return all;
    }
}
