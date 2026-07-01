package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.ProjectHseMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectHseMetricsRepository extends JpaRepository<ProjectHseMetrics, UUID> {

    Optional<ProjectHseMetrics> findByProjectId(UUID projectId);
}
