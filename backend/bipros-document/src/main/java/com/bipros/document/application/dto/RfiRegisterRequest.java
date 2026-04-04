package com.bipros.document.application.dto;

import com.bipros.document.domain.model.RfiPriority;
import com.bipros.document.domain.model.RfiStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RfiRegisterRequest(
    @NotBlank(message = "RFI number is required")
    String rfiNumber,

    @NotBlank(message = "Subject is required")
    String subject,

    String description,

    @NotBlank(message = "Raised by is required")
    String raisedBy,

    @NotBlank(message = "Assigned to is required")
    String assignedTo,

    @NotNull(message = "Raised date is required")
    LocalDate raisedDate,

    @NotNull(message = "Due date is required")
    LocalDate dueDate,

    LocalDate closedDate,

    RfiStatus status,

    RfiPriority priority,

    String response
) {
}
