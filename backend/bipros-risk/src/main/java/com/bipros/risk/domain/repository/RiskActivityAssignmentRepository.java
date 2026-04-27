package com.bipros.risk.domain.repository;

import com.bipros.risk.domain.model.RiskActivityAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskActivityAssignmentRepository extends JpaRepository<RiskActivityAssignment, UUID> {
    List<RiskActivityAssignment> findByRiskId(UUID riskId);
    List<RiskActivityAssignment> findByActivityId(UUID activityId);
    List<RiskActivityAssignment> findByProjectId(UUID projectId);
    Optional<RiskActivityAssignment> findByRiskIdAndActivityId(UUID riskId, UUID activityId);
    void deleteByRiskIdAndActivityId(UUID riskId, UUID activityId);
    boolean existsByRiskIdAndActivityId(UUID riskId, UUID activityId);
}
