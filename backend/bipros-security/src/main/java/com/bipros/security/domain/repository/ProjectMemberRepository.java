package com.bipros.security.domain.repository;

import com.bipros.security.domain.model.ProjectMember;
import com.bipros.security.domain.model.ProjectMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember> findByUserId(UUID userId);

    List<ProjectMember> findByProjectId(UUID projectId);

    Optional<ProjectMember> findByUserIdAndProjectIdAndProjectRole(
            UUID userId, UUID projectId, ProjectMemberRole role);

    List<ProjectMember> findByUserIdAndProjectId(UUID userId, UUID projectId);

    @Query("SELECT DISTINCT pm.projectId FROM ProjectMember pm WHERE pm.userId = ?1")
    Set<UUID> findProjectIdsByUserId(UUID userId);

    boolean existsByUserIdAndProjectIdAndProjectRole(
            UUID userId, UUID projectId, ProjectMemberRole role);

    void deleteByUserIdAndProjectId(UUID userId, UUID projectId);
}
