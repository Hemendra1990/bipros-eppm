package com.bipros.reporting.domain.repository;

import com.bipros.reporting.domain.model.ReportDefinition;
import com.bipros.reporting.domain.model.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {
  List<ReportDefinition> findByReportType(ReportType reportType);

  List<ReportDefinition> findByIsBuiltInTrue();

  List<ReportDefinition> findByCreatedByUserId(UUID createdByUserId);
}
