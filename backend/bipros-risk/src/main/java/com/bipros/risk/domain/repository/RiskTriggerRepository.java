package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.RiskTrigger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RiskTriggerRepository extends JpaRepository<RiskTrigger, UUID> {
    List<RiskTrigger> findByProjectId(UUID projectId);

    List<RiskTrigger> findByRiskId(UUID riskId);

    List<RiskTrigger> findByProjectIdAndIsTriggeredTrue(UUID projectId);

    List<RiskTrigger> findByRiskIdAndIsTriggeredTrue(UUID riskId);
}
