package com.bipros.resource.application.dto;

import com.bipros.resource.domain.algorithm.LevelingMode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ResourceLevelingResponse(
        LevelingMode mode,
        int activitiesShifted,
        int iterationsUsed,
        double peakUtilizationBefore,
        double peakUtilizationAfter,
        List<ShiftedActivity> shiftedActivities,
        List<String> messages
) {
    public record ShiftedActivity(
            UUID activityId,
            LocalDate originalStart,
            LocalDate newStart,
            long delayDays
    ) {}
}
