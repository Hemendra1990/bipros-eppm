package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ResourceAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ResourceAssignmentRepository extends JpaRepository<ResourceAssignment, UUID> {

  Page<ResourceAssignment> findByUpdatedAtAfter(Instant since, Pageable pageable);


  List<ResourceAssignment> findByActivityId(UUID activityId);

  List<ResourceAssignment> findByResourceId(UUID resourceId);

  List<ResourceAssignment> findByProjectId(UUID projectId);

  List<ResourceAssignment> findByResourceIdAndPlannedStartDateBetween(
      UUID resourceId, LocalDate startDate, LocalDate endDate);
}
