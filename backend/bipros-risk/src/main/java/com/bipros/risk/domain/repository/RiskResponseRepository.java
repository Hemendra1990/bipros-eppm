package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.RiskResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RiskResponseRepository extends JpaRepository<RiskResponse, UUID> {
    List<RiskResponse> findByRiskId(UUID riskId);

    /** Batch fetch used by the risk-list path to avoid N+1 when computing analysis-quality. */
    List<RiskResponse> findByRiskIdIn(Collection<UUID> riskIds);
}
