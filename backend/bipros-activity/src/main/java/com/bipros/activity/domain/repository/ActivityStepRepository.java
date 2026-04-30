package com.bipros.activity.domain.repository;

import com.bipros.activity.domain.model.ActivityStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityStepRepository extends JpaRepository<ActivityStep, UUID> {
  List<ActivityStep> findByActivityIdOrderBySortOrder(UUID activityId);

  long countByActivityId(UUID activityId);
}
