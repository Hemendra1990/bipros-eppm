package com.bipros.reporting.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_executions", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReportExecution extends BaseEntity {

  @Column(name = "report_definition_id", nullable = false)
  private UUID reportDefinitionId;

  @Column(name = "project_id")
  private UUID projectId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReportFormat format;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReportStatus status = ReportStatus.PENDING;

  @Column(columnDefinition = "TEXT")
  private String parameters;

  @Column(name = "result_data", columnDefinition = "TEXT")
  private String resultData;

  @Column(name = "file_path")
  private String filePath;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;
}
