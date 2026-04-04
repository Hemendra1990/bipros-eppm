package com.bipros.importexport.domain.repository;

import com.bipros.importexport.domain.model.ImportExportDirection;
import com.bipros.importexport.domain.model.ImportExportJob;
import com.bipros.importexport.domain.model.ImportExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportExportJobRepository extends JpaRepository<ImportExportJob, UUID> {
  List<ImportExportJob> findByDirection(ImportExportDirection direction);

  List<ImportExportJob> findByProjectId(UUID projectId);

  List<ImportExportJob> findByStatus(ImportExportStatus status);
}
