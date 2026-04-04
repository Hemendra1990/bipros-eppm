package com.bipros.activity.domain.repository;

import com.bipros.activity.domain.model.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {
  List<Activity> findByProjectId(UUID projectId);

  List<Activity> findByWbsNodeId(UUID wbsNodeId);

  List<Activity> findByProjectIdAndIsCritical(UUID projectId, Boolean isCritical);

  long countByProjectId(UUID projectId);

  Page<Activity> findByProjectIdOrderBySortOrder(UUID projectId, Pageable pageable);
}
