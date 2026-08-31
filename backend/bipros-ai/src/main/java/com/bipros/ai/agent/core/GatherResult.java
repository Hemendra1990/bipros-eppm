package com.bipros.ai.agent.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * The deterministic output of an agent's {@link Agent#gather} pass: a JSON snapshot of the
 * data the agent looked at (used for the change-detection data hash and for the narrator
 * prompt) plus the list of candidate findings (which the narrator may reword/rank but not
 * invent or renumber).
 */
public record GatherResult(JsonNode dataSnapshot, List<AgentFindingDraft> candidates) {

    public GatherResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }
}
