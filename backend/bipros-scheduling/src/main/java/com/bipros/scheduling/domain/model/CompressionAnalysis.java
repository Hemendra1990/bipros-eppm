package com.bipros.scheduling.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "compression_analyses", schema = "scheduling")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompressionAnalysis extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "scenario_id")
  private UUID scenarioId;

  @Column(name = "analysis_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private CompressionType analysisType;

  @Column(name = "original_duration")
  private Double originalDuration;

  @Column(name = "compressed_duration")
  private Double compressedDuration;

  @Column(name = "duration_saved")
  private Double durationSaved;

  @Column(name = "additional_cost", precision = 19, scale = 2)
  private BigDecimal additionalCost;

  @Column(columnDefinition = "TEXT")
  private String recommendations;
}
