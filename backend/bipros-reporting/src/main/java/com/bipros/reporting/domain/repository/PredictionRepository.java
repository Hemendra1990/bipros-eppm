package com.bipros.reporting.domain.repository;

import com.bipros.reporting.domain.model.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, UUID> {
  List<Prediction> findByProjectIdOrderByCalculatedAtDesc(UUID projectId);

  List<Prediction> findByProjectIdAndPredictionTypeOrderByCalculatedAtDesc(
      UUID projectId, Prediction.PredictionType predictionType);
}
