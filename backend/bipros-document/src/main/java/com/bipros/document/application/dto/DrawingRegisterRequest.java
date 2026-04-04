package com.bipros.document.application.dto;

import com.bipros.document.domain.model.DrawingDiscipline;
import com.bipros.document.domain.model.DrawingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record DrawingRegisterRequest(
    @NotBlank(message = "Drawing number is required")
    String drawingNumber,

    @NotBlank(message = "Title is required")
    String title,

    @NotNull(message = "Discipline is required")
    DrawingDiscipline discipline,

    @NotBlank(message = "Revision is required")
    String revision,

    @NotNull(message = "Revision date is required")
    LocalDate revisionDate,

    DrawingStatus status,

    @NotBlank(message = "Package code is required")
    String packageCode,

    @NotBlank(message = "Scale is required")
    String scale,

    UUID documentId
) {
}
