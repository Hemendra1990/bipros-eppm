package com.bipros.reporting.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "predictions", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Prediction extends BaseEntity {

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "prediction_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private PredictionType predictionType;

  @Column(name = "predicted_value", nullable = false)
  private Double predictedValue;

  @Column(name = "confidence_level", nullable = false)
  private Double confidenceLevel;

  @Column(name = "baseline_value")
  private Double baselineValue;

  @Column(name = "variance")
  private Double variance;

  @Column(name = "factors", columnDefinition = "TEXT")
  private String factors;

  @Column(name = "model_version", nullable = false)
  private String modelVersion;

  @Column(name = "calculated_at", nullable = false)
  private Instant calculatedAt;

  public enum PredictionType {
    SCHEDULE_SLIP,
    COST_OVERRUN,
    COMPLETION_DATE,
    RISK_PROBABILITY
  }
}
