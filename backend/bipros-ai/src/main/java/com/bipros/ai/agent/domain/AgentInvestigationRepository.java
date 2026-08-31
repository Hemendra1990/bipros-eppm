package com.bipros.ai.agent.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AgentInvestigationRepository extends JpaRepository<AgentInvestigation, UUID> {

    Page<AgentInvestigation> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}
