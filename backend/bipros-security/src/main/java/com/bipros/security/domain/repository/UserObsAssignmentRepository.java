package com.bipros.security.domain.repository;

import com.bipros.security.domain.model.UserObsAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserObsAssignmentRepository extends JpaRepository<UserObsAssignment, UUID> {

    List<UserObsAssignment> findByUserId(UUID userId);

    List<UserObsAssignment> findByObsNodeId(UUID obsNodeId);

    Optional<UserObsAssignment> findByUserIdAndObsNodeId(UUID userId, UUID obsNodeId);
}
