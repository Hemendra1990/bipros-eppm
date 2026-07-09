package com.bipros.scheduling.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Result of a deterministic schedule what-if / change-impact simulation. Compares the current
 * (baseline) CPM run against a scenario CPM run with the requested duration changes applied, and
 * reports the impact on the project finish date and the critical path.
 */
public record WhatIfResponse(
        String scenarioLabel,
        LocalDate baselineFinish,
        LocalDate scenarioFinish,
        double deltaWorkingDays,
        int baselineCriticalCount,
        int scenarioCriticalCount,
        List<ActivityImpact> newlyCritical,
        List<ActivityImpact> changedActivities) {

    public record ActivityImpact(
            UUID activityId,
            String activityName,
            LocalDate baselineFinish,
            LocalDate scenarioFinish,
            long shiftDays,
            boolean critical) {}
}
