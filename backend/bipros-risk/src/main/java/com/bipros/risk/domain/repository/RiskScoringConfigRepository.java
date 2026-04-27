package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.RiskScoringConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskScoringConfigRepository extends JpaRepository<RiskScoringConfig, UUID> {
    Optional<RiskScoringConfig> findByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
