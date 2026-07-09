package com.bipros.ai.agent.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key for {@link AgentTriggerQueueItem}: one queued pipeline per project. */
public class AgentTriggerQueueId implements Serializable {

    private String pipelineKey;
    private UUID projectId;

    public AgentTriggerQueueId() {
    }

    public AgentTriggerQueueId(String pipelineKey, UUID projectId) {
        this.pipelineKey = pipelineKey;
        this.projectId = projectId;
    }

    public String getPipelineKey() {
        return pipelineKey;
    }

    public UUID getProjectId() {
        return projectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentTriggerQueueId that)) return false;
        return Objects.equals(pipelineKey, that.pipelineKey) && Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pipelineKey, projectId);
    }
}
