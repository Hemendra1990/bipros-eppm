package com.bipros.gis.domain.repository;

import com.bipros.gis.domain.model.ConstructionProgressSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConstructionProgressSnapshotRepository extends JpaRepository<ConstructionProgressSnapshot, UUID> {
    List<ConstructionProgressSnapshot> findByProjectId(UUID projectId);
    List<ConstructionProgressSnapshot> findByWbsPolygonIdOrderByCaptureDate(UUID wbsPolygonId);
    List<ConstructionProgressSnapshot> findByProjectIdAndCaptureDateBetween(UUID projectId, LocalDate fromDate, LocalDate toDate);
    Optional<ConstructionProgressSnapshot> findTopByWbsPackageCodeOrderByCaptureDateDesc(String wbsPackageCode);
}
