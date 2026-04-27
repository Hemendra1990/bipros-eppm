package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RiskRepository extends JpaRepository<Risk, UUID>, JpaSpecificationExecutor<Risk> {
    @EntityGraph(attributePaths = {"category"})
    List<Risk> findByProjectId(UUID projectId);

    @EntityGraph(attributePaths = {"category"})
    List<Risk> findByProjectIdAndStatus(UUID projectId, RiskStatus status);

    List<Risk> findByProjectIdOrderByRiskScoreDesc(UUID projectId);

    long countByProjectId(UUID projectId);

    /**
     * Largest existing numeric suffix in {@code RISK-NNNN} codes for the project, or 0 if none.
     * Used by code generation so deleted rows don't create duplicate codes.
     */
    @Query(value = """
        SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM '[0-9]+$') AS INTEGER)), 0)
        FROM risk.risks
        WHERE project_id = :projectId AND code ~ '^RISK-[0-9]+$'
        """, nativeQuery = true)
    int maxRiskCodeNumber(UUID projectId);
}
