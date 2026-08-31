package com.bipros.activity.domain.repository;

import com.bipros.activity.domain.model.ActivityCodeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityCodeAssignmentRepository extends JpaRepository<ActivityCodeAssignment, UUID> {
  List<ActivityCodeAssignment> findByActivityId(UUID activityId);

  List<ActivityCodeAssignment> findByActivityCodeId(UUID activityCodeId);

  @Modifying
  void deleteByActivityIdIn(Collection<UUID> activityIds);

  long countByActivityIdIn(Collection<UUID> activityIds);
}
