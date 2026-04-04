package com.bipros.importexport.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_export_jobs", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ImportExportJob extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ImportExportFormat format;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ImportExportDirection direction;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "file_path")
  private String filePath;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ImportExportStatus status = ImportExportStatus.PENDING;

  @Column(name = "total_records")
  private Integer totalRecords;

  @Column(name = "processed_records")
  private Integer processedRecords = 0;

  @Column(name = "error_count")
  private Integer errorCount = 0;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "error_log", columnDefinition = "TEXT")
  private String errorLog;

  @Column(name = "imported_project_id")
  private UUID importedProjectId;
}
