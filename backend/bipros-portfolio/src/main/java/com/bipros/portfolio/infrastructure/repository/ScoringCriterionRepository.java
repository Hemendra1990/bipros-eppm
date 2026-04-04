package com.bipros.portfolio.infrastructure.repository;

import com.bipros.portfolio.domain.ScoringCriterion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScoringCriterionRepository extends JpaRepository<ScoringCriterion, UUID> {

  List<ScoringCriterion> findByScoringModelIdOrderBySortOrder(UUID scoringModelId);
}
