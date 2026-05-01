package com.bipros.resource.domain.repository;

import com.bipros.resource.domain.model.ProjectResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectResourceRepository extends JpaRepository<ProjectResource, UUID> {

    List<ProjectResource> findByProjectId(UUID projectId);

    Optional<ProjectResource> findByProjectIdAndResourceId(UUID projectId, UUID resourceId);

    boolean existsByProjectIdAndResourceId(UUID projectId, UUID resourceId);

    void deleteByProjectIdAndResourceId(UUID projectId, UUID resourceId);

    List<ProjectResource> findByProjectIdAndResourceIdIn(UUID projectId, List<UUID> resourceIds);
}
