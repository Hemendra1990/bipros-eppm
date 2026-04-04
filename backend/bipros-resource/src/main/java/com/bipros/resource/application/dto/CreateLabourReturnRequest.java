package com.bipros.resource.application.dto;

import com.bipros.resource.domain.model.SkillCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record CreateLabourReturnRequest(
    @NotNull UUID projectId,
    @NotNull String contractorName,
    @NotNull LocalDate returnDate,
    @NotNull SkillCategory skillCategory,
    @NotNull @Positive Integer headCount,
    @NotNull @Positive Double manDays,
    UUID wbsNodeId,
    String siteLocation,
    String remarks
) {}
