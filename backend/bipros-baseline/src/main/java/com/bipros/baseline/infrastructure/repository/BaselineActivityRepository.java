package com.bipros.baseline.infrastructure.repository;

import com.bipros.baseline.domain.BaselineActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BaselineActivityRepository extends JpaRepository<BaselineActivity, UUID> {

  List<BaselineActivity> findByBaselineId(UUID baselineId);

  Optional<BaselineActivity> findByBaselineIdAndActivityId(UUID baselineId, UUID activityId);
}
