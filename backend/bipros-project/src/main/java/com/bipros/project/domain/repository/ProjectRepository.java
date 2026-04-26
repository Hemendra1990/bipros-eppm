package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    List<Project> findByEpsNodeId(UUID epsNodeId);

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    boolean existsByCode(String code);

    Optional<Project> findByCode(String code);
}
