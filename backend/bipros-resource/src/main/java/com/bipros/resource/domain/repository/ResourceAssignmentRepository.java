package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ResourceAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceAssignmentRepository extends JpaRepository<ResourceAssignment, UUID> {

  List<ResourceAssignment> findByActivityId(UUID activityId);

  List<ResourceAssignment> findByResourceId(UUID resourceId);

  List<ResourceAssignment> findByProjectId(UUID projectId);

  List<ResourceAssignment> findByResourceIdAndPlannedStartDateBetween(
      UUID resourceId, LocalDate startDate, LocalDate endDate);

  Optional<ResourceAssignment> findByProjectIdAndActivityIdAndResourceId(
      UUID projectId, UUID activityId, UUID resourceId);

  Optional<ResourceAssignment> findByActivityIdAndResourceIdIsNullAndRoleId(
      UUID activityId, UUID roleId);

  @Query("select coalesce(sum(ra.plannedUnits), 0) from ResourceAssignment ra where ra.activityId = :activityId")
  Double sumPlannedUnitsByActivityId(@Param("activityId") UUID activityId);

  @Query("select coalesce(sum(ra.actualUnits), 0) from ResourceAssignment ra where ra.activityId = :activityId")
  Double sumActualUnitsByActivityId(@Param("activityId") UUID activityId);
}
