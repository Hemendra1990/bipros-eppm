package com.bipros.gis.domain.repository;

import com.bipros.gis.domain.model.SatelliteSceneIngestionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SatelliteSceneIngestionLogRepository extends JpaRepository<SatelliteSceneIngestionLog, UUID> {

    List<SatelliteSceneIngestionLog> findByProjectIdOrderByRunStartedAtDesc(UUID projectId);
}
