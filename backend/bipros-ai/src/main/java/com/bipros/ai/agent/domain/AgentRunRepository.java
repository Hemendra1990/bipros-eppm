package com.bipros.ai.agent.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    /** Last run of this agent for this project in a given status — used for the change-detection data hash. */
    Optional<AgentRun> findFirstByAgentKeyAndProjectIdAndStatusOrderByStartedAtDesc(
            String agentKey, UUID projectId, AgentRunStatus status);

    /** Portfolio variant (projectId IS NULL) — derived-query form avoids the Postgres nullable-param cast pitfall. */
    Optional<AgentRun> findFirstByAgentKeyAndProjectIdIsNullAndStatusOrderByStartedAtDesc(
            String agentKey, AgentRunStatus status);

    /** Most recent run of this agent for a project, any status — for the registry "last run" summary. */
    Optional<AgentRun> findFirstByAgentKeyAndProjectIdOrderByStartedAtDesc(String agentKey, UUID projectId);

    Page<AgentRun> findByProjectIdOrderByStartedAtDesc(UUID projectId, Pageable pageable);

    Page<AgentRun> findByProjectIdAndAgentKeyOrderByStartedAtDesc(UUID projectId, String agentKey, Pageable pageable);

    Page<AgentRun> findByProjectIdAndStatusOrderByStartedAtDesc(UUID projectId, AgentRunStatus status, Pageable pageable);

    Page<AgentRun> findByProjectIdAndAgentKeyAndStatusOrderByStartedAtDesc(
            UUID projectId, String agentKey, AgentRunStatus status, Pageable pageable);

    /** Portfolio activity feed — recent runs across a set of accessible projects. */
    List<AgentRun> findByProjectIdInOrderByStartedAtDesc(Collection<UUID> projectIds, Pageable pageable);

    /** Portfolio activity feed for ADMIN (no project filter). */
    List<AgentRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
