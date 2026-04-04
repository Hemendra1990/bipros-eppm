package com.bipros.portfolio.infrastructure.repository;

import com.bipros.portfolio.domain.ProjectScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectScoreRepository extends JpaRepository<ProjectScore, UUID> {

  List<ProjectScore> findByProjectIdAndScoringModelId(UUID projectId, UUID scoringModelId);

  Optional<ProjectScore> findByProjectIdAndScoringCriterionId(UUID projectId, UUID criterionId);

  void deleteByProjectId(UUID projectId);
}
