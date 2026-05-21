package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ActivitySubContractorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivitySubContractorAssignmentRepository
    extends JpaRepository<ActivitySubContractorAssignment, UUID> {

  List<ActivitySubContractorAssignment> findByProjectIdAndActivityId(UUID projectId, UUID activityId);

  long countBySubContractorMasterId(UUID subContractorMasterId);
}
