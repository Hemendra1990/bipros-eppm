package com.bipros.importexport.domain.repository;

import com.bipros.importexport.domain.model.ImportExportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportExportLogRepository extends JpaRepository<ImportExportLog, UUID> {
  List<ImportExportLog> findByJobId(UUID jobId);

  List<ImportExportLog> findByJobIdAndLevel(UUID jobId, String level);
}
