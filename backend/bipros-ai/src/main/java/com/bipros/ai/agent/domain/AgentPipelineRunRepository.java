package com.bipros.ai.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentPipelineRunRepository extends JpaRepository<AgentPipelineRun, UUID> {

    boolean existsByPipelineKeyAndProjectIdAndStatus(String pipelineKey, UUID projectId, PipelineRunStatus status);

    Optional<AgentPipelineRun> findFirstByPipelineKeyAndProjectIdAndStatusOrderByStartedAtDesc(
            String pipelineKey, UUID projectId, PipelineRunStatus status);
}
