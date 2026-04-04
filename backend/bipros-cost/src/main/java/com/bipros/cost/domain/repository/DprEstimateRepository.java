package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.DprEstimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DprEstimateRepository extends JpaRepository<DprEstimate, UUID> {
    List<DprEstimate> findByProjectIdOrderByCreatedAt(UUID projectId);
    List<DprEstimate> findByWbsNodeId(UUID wbsNodeId);
}
