package com.bipros.contract.domain.repository;

import com.bipros.contract.domain.model.ProcurementPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcurementPlanRepository extends JpaRepository<ProcurementPlan, UUID> {
    Page<ProcurementPlan> findByProjectId(UUID projectId, Pageable pageable);
    List<ProcurementPlan> findByProjectId(UUID projectId);
    List<ProcurementPlan> findByWbsNodeId(UUID wbsNodeId);
}
