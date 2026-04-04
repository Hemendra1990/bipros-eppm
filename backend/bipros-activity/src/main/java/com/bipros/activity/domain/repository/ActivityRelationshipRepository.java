package com.bipros.activity.domain.repository;

import com.bipros.activity.domain.model.ActivityRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRelationshipRepository extends JpaRepository<ActivityRelationship, UUID> {
  List<ActivityRelationship> findByPredecessorActivityId(UUID predecessorActivityId);

  List<ActivityRelationship> findBySuccessorActivityId(UUID successorActivityId);

  List<ActivityRelationship> findByProjectId(UUID projectId);

  boolean existsByPredecessorActivityIdAndSuccessorActivityId(UUID predecessorId, UUID successorId);
}
