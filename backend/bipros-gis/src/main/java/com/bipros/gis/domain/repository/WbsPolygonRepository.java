package com.bipros.gis.domain.repository;

import com.bipros.gis.domain.model.WbsPolygon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WbsPolygonRepository extends JpaRepository<WbsPolygon, UUID> {
    List<WbsPolygon> findByProjectId(UUID projectId);
    Optional<WbsPolygon> findByProjectIdAndWbsNodeId(UUID projectId, UUID wbsNodeId);
    List<WbsPolygon> findByProjectIdAndLayerId(UUID projectId, UUID layerId);
}
