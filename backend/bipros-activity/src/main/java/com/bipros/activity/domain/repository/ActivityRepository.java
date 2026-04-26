package com.bipros.activity.domain.repository;

import com.bipros.activity.domain.model.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID>, JpaSpecificationExecutor<Activity> {
  List<Activity> findByProjectId(UUID projectId);

  List<Activity> findByWbsNodeId(UUID wbsNodeId);

  List<Activity> findByProjectIdAndIsCritical(UUID projectId, Boolean isCritical);

  long countByProjectId(UUID projectId);

  long countByWbsNodeId(UUID wbsNodeId);

  long countByCalendarId(UUID calendarId);

  Page<Activity> findByProjectIdOrderBySortOrder(UUID projectId, Pageable pageable);
}
