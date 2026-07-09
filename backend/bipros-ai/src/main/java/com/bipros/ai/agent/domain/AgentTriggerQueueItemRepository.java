package com.bipros.ai.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentTriggerQueueItemRepository
        extends JpaRepository<AgentTriggerQueueItem, AgentTriggerQueueId> {

    Optional<AgentTriggerQueueItem> findByPipelineKeyAndProjectId(String pipelineKey, UUID projectId);

    /** Rows due for dispatch: {@code now >= min(dueAt, maxDueAt)}. */
    List<AgentTriggerQueueItem> findByDueAtLessThanEqualOrMaxDueAtLessThanEqual(Instant dueCutoff, Instant maxCutoff);
}
