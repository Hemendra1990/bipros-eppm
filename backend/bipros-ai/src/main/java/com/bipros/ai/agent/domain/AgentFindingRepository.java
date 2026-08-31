package com.bipros.ai.agent.domain;

import com.bipros.ai.agent.core.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentFindingRepository extends JpaRepository<AgentFinding, UUID> {

    /** The current ACTIVE finding for a fingerprint, if any — the dedup/supersession anchor. */
    Optional<AgentFinding> findByFingerprintAndStatus(String fingerprint, FindingStatus status);

    List<AgentFinding> findByRunId(UUID runId);

    List<AgentFinding> findByRunIdAndNotifiableTrue(UUID runId);

    /** Cross-agent memory reads: active findings for a project produced by any of the given agents. */
    List<AgentFinding> findByProjectIdAndAgentKeyInAndStatus(
            UUID projectId, Collection<String> agentKeys, FindingStatus status);

    List<AgentFinding> findByProjectIdAndStatus(UUID projectId, FindingStatus status);

    /** Notification routing: the project's currently-notifiable findings in a given status. */
    List<AgentFinding> findByProjectIdAndStatusAndNotifiableTrue(UUID projectId, FindingStatus status);

    /** Daily digest: every still-notifiable finding across all projects in a given status. */
    List<AgentFinding> findByStatusAndNotifiableTrue(FindingStatus status);

    Page<AgentFinding> findByProjectIdAndStatusOrderBySeverityDescLastSeenAtDesc(
            UUID projectId, FindingStatus status, Pageable pageable);

    Page<AgentFinding> findByProjectIdAndStatusAndSeverityOrderByLastSeenAtDesc(
            UUID projectId, FindingStatus status, Severity severity, Pageable pageable);

    Page<AgentFinding> findByProjectIdAndAgentKeyAndStatusOrderByLastSeenAtDesc(
            UUID projectId, String agentKey, FindingStatus status, Pageable pageable);

    /** Portfolio reads across an explicit set of accessible projects. */
    List<AgentFinding> findByProjectIdInAndStatus(Collection<UUID> projectIds, FindingStatus status);

    /** TTL sweep: ACTIVE findings whose validUntil has passed. */
    List<AgentFinding> findByStatusAndValidUntilBefore(FindingStatus status, Instant cutoff);

    /** Historical-learning: same finding type resolved on OTHER projects (cross-project precedent). */
    List<AgentFinding> findByFindingTypeAndStatusAndProjectIdNot(
            String findingType, FindingStatus status, UUID projectId);
}
