package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.model.RiskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RiskRepository extends JpaRepository<Risk, UUID> {
    List<Risk> findByProjectId(UUID projectId);

    List<Risk> findByProjectIdAndStatus(UUID projectId, RiskStatus status);

    List<Risk> findByProjectIdOrderByRiskScoreDesc(UUID projectId);
}
