package com.bipros.reporting.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduleHealthRow(
    UUID projectId,
    String projectCode,
    String projectName,
    long missingLogicCount,
    long leadRelationshipsCount,
    long lagsCount,
    double fsRelationshipPct,
    long hardConstraintsCount,
    long highFloatCount,
    long negativeFloatCount,
    long invalidDatesCount,
    long resourceAllocationIssues,
    long missedTasksCount,
    boolean criticalPathTestOk,
    long criticalPathLength,
    double beiActual,
    double beiRequired,
    double overallHealthPct) {}
