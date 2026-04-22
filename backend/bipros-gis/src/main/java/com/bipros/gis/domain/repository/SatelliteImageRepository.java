package com.bipros.gis.domain.repository;

import com.bipros.gis.domain.model.SatelliteImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SatelliteImageRepository extends JpaRepository<SatelliteImage, UUID> {
    List<SatelliteImage> findByProjectIdOrderByCaptureDate(UUID projectId);
    List<SatelliteImage> findByProjectIdAndCaptureDateBetween(UUID projectId, LocalDate fromDate, LocalDate toDate);
    List<SatelliteImage> findByLayerIdOrderByCaptureDate(UUID layerId);

    /** Used by ingestion to dedupe scenes that a prior run already pulled. */
    boolean existsBySceneId(String sceneId);
}
