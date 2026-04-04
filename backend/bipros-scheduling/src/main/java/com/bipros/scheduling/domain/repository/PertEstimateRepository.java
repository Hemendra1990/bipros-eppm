package com.bipros.scheduling.domain.repository;

import com.bipros.scheduling.domain.model.PertEstimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PertEstimateRepository extends JpaRepository<PertEstimate, UUID> {
  Optional<PertEstimate> findByActivityId(UUID activityId);

  List<PertEstimate> findByActivityIdIn(List<UUID> activityIds);
}
