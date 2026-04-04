package com.bipros.gis.domain.repository;

import com.bipros.gis.domain.model.GisLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GisLayerRepository extends JpaRepository<GisLayer, UUID> {
    List<GisLayer> findByProjectIdOrderBySortOrder(UUID projectId);
}
