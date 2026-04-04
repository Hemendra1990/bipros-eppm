package com.bipros.cost.domain.repository;

import com.bipros.cost.domain.entity.ProjectFunding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectFundingRepository extends JpaRepository<ProjectFunding, UUID> {
    List<ProjectFunding> findByProjectId(UUID projectId);
    List<ProjectFunding> findByFundingSourceId(UUID fundingSourceId);
    List<ProjectFunding> findByProjectIdAndWbsNodeId(UUID projectId, UUID wbsNodeId);
}
