package com.bipros.scheduling.application.dto;

import com.bipros.scheduling.domain.model.PertEstimate;

import java.util.UUID;

public record PertEstimateResponse(
    UUID id,
    UUID activityId,
    Double optimisticDuration,
    Double mostLikelyDuration,
    Double pessimisticDuration,
    Double expectedDuration,
    Double standardDeviation,
    Double variance
) {
  public static PertEstimateResponse from(PertEstimate estimate) {
    return new PertEstimateResponse(
        estimate.getId(),
        estimate.getActivityId(),
        estimate.getOptimisticDuration(),
        estimate.getMostLikelyDuration(),
        estimate.getPessimisticDuration(),
        estimate.getExpectedDuration(),
        estimate.getStandardDeviation(),
        estimate.getVariance()
    );
  }
}
