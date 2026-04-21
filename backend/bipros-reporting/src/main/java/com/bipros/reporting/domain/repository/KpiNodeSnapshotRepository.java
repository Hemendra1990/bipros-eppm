package com.bipros.reporting.domain.repository;

import com.bipros.reporting.domain.model.KpiNodeSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KpiNodeSnapshotRepository extends JpaRepository<KpiNodeSnapshot, UUID> {

    List<KpiNodeSnapshot> findByKpiCodeOrderByNodeCode(String kpiCode);

    List<KpiNodeSnapshot> findByNodeCodeOrderByKpiCode(String nodeCode);

    List<KpiNodeSnapshot> findByPeriodOrderByKpiCodeAscNodeCodeAsc(String period);
}
