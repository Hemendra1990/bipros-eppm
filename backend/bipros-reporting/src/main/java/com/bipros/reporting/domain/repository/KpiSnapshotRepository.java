package com.bipros.reporting.domain.repository;

import com.bipros.reporting.domain.model.KpiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KpiSnapshotRepository extends JpaRepository<KpiSnapshot, UUID> {
    List<KpiSnapshot> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    List<KpiSnapshot> findByKpiDefinitionIdAndProjectId(UUID kpiDefinitionId, UUID projectId);
    List<KpiSnapshot> findByProjectIdAndPeriodOrderByCreatedAtDesc(UUID projectId, String period);
}
