package com.bipros.reporting.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectStatusSnapshotDto(
    UUID projectId,
    String projectCode,
    String projectName,
    String status,
    LocalDate plannedStart,
    LocalDate plannedFinish,
    String scheduleRag,
    String costRag,
    String scopeRag,
    String riskRag,
    String hseRag,
    List<String> topIssues,
    String nextMilestoneName,
    LocalDate nextMilestoneDate,
    double currentCpi,
    double currentSpi,
    double physicalPct,
    double plannedPct,
    long activeRisksCount,
    long openHseIncidents,
    BigDecimal bacCrores,
    BigDecimal eacCrores,
    Instant lastUpdatedAt) {}
