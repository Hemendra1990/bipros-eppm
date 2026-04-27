package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.RiskScoringMatrix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskScoringMatrixRepository extends JpaRepository<RiskScoringMatrix, UUID> {
    List<RiskScoringMatrix> findByProjectId(UUID projectId);
    Optional<RiskScoringMatrix> findByProjectIdAndProbabilityValueAndImpactValue(
            UUID projectId, Integer probabilityValue, Integer impactValue);
    void deleteByProjectId(UUID projectId);
}
