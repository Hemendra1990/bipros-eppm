package com.bipros.ai.agent.domain;

import com.bipros.ai.agent.core.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentNotificationRuleRepository extends JpaRepository<AgentNotificationRule, UUID> {

    Optional<AgentNotificationRule> findByProjectIdAndSeverity(UUID projectId, Severity severity);

    /** Global default rule for a severity (projectId IS NULL). */
    Optional<AgentNotificationRule> findByProjectIdIsNullAndSeverity(Severity severity);

    List<AgentNotificationRule> findByProjectId(UUID projectId);
}
