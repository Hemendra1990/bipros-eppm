package com.bipros.reporting.application.dto;

import com.bipros.reporting.domain.model.Prediction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionDto {

  private UUID id;
  private UUID projectId;
  private String predictionType;
  private Double predictedValue;
  private Double confidenceLevel;
  private Double baselineValue;
  private Double variance;
  private String factors;
  private String modelVersion;
  private Instant calculatedAt;

  public static PredictionDto from(Prediction prediction) {
    return PredictionDto.builder()
        .id(prediction.getId())
        .projectId(prediction.getProjectId())
        .predictionType(prediction.getPredictionType().toString())
        .predictedValue(prediction.getPredictedValue())
        .confidenceLevel(prediction.getConfidenceLevel())
        .baselineValue(prediction.getBaselineValue())
        .variance(prediction.getVariance())
        .factors(prediction.getFactors())
        .modelVersion(prediction.getModelVersion())
        .calculatedAt(prediction.getCalculatedAt())
        .build();
  }
}
