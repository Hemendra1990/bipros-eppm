package com.bipros.project.application.dto;

import com.bipros.project.domain.model.ProjectStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    String code,
    String name,
    String description,
    UUID epsNodeId,
    UUID obsNodeId,
    LocalDate plannedStartDate,
    LocalDate plannedFinishDate,
    LocalDate dataDate,
    ProjectStatus status,
    LocalDate mustFinishByDate,
    Integer priority,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
