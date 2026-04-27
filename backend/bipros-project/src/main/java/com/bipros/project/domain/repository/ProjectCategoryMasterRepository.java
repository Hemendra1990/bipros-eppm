package com.bipros.project.domain.repository;

import com.bipros.project.domain.model.ProjectCategoryMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectCategoryMasterRepository extends JpaRepository<ProjectCategoryMaster, UUID> {

    Optional<ProjectCategoryMaster> findByCode(String code);

    List<ProjectCategoryMaster> findByActiveTrueOrderBySortOrderAsc();

    boolean existsByCode(String code);
}
