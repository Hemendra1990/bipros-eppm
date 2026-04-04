package com.bipros.scheduling.application.dto;

import java.util.UUID;

public record PertEstimateRequest(
    UUID activityId,
    Double optimisticDuration,
    Double mostLikelyDuration,
    Double pessimisticDuration
) {}
