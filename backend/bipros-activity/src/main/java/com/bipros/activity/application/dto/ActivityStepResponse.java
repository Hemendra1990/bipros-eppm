package com.bipros.activity.application.dto;

import com.bipros.activity.domain.model.ActivityStep;

import java.util.UUID;

public record ActivityStepResponse(
    UUID id,
    UUID activityId,
    String name,
    String description,
    Double weight,
    Double weightPercent,
    Boolean isCompleted,
    Integer sortOrder
) {
  public static ActivityStepResponse from(ActivityStep step) {
    return new ActivityStepResponse(
        step.getId(),
        step.getActivityId(),
        step.getName(),
        step.getDescription(),
        step.getWeight(),
        step.getWeightPercent(),
        step.getIsCompleted(),
        step.getSortOrder()
    );
  }
}
