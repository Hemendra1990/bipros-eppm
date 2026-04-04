package com.bipros.project.application.dto;

import com.bipros.project.domain.model.ProjectStatus;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateProjectRequest(
    String name,

    String description,

    UUID obsNodeId,

    LocalDate plannedStartDate,

    LocalDate plannedFinishDate,

    LocalDate mustFinishByDate,

    ProjectStatus status,

    Integer priority,

    LocalDate dataDate
) {
}
