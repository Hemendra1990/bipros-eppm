package com.bipros.importexport.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "import_export_logs", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ImportExportLog extends BaseEntity {

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(length = 20)
  private String level;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String message;

  @Column(name = "entity_type", length = 50)
  private String entityType;

  @Column(name = "entity_id")
  private String entityId;

  @Column(name = "line_number")
  private Integer lineNumber;
}
