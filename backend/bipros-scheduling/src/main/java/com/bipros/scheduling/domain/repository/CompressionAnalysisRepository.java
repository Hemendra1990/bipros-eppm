package com.bipros.scheduling.domain.repository;

import com.bipros.scheduling.domain.model.CompressionAnalysis;
import com.bipros.scheduling.domain.model.CompressionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompressionAnalysisRepository extends JpaRepository<CompressionAnalysis, UUID> {

  List<CompressionAnalysis> findByProjectId(UUID projectId);

  Optional<CompressionAnalysis> findByProjectIdAndAnalysisType(UUID projectId, CompressionType analysisType);

  List<CompressionAnalysis> findByScenarioId(UUID scenarioId);
}
