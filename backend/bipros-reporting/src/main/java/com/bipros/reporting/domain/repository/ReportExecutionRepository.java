package com.bipros.reporting.domain.repository;

import com.bipros.reporting.domain.model.ReportExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportExecutionRepository extends JpaRepository<ReportExecution, UUID> {
  List<ReportExecution> findByReportDefinitionId(UUID reportDefinitionId);

  List<ReportExecution> findByProjectIdOrderByExecutedAtDesc(UUID projectId);
}
