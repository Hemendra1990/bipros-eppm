package com.bipros.baseline.infrastructure.repository;

import com.bipros.baseline.domain.BaselineResourceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BaselineResourceAssignmentRepository
    extends JpaRepository<BaselineResourceAssignment, UUID> {

  List<BaselineResourceAssignment> findByBaselineId(UUID baselineId);

  List<BaselineResourceAssignment> findByBaselineIdAndActivityId(UUID baselineId, UUID activityId);

  void deleteByBaselineId(UUID baselineId);
}
