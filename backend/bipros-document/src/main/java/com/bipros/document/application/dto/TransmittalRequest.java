package com.bipros.document.application.dto;

import com.bipros.document.domain.model.TransmittalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TransmittalRequest(
    @NotBlank(message = "Transmittal number is required")
    String transmittalNumber,

    @NotBlank(message = "Subject is required")
    String subject,

    @NotBlank(message = "From party is required")
    String fromParty,

    @NotBlank(message = "To party is required")
    String toParty,

    @NotNull(message = "Sent date is required")
    LocalDate sentDate,

    @NotNull(message = "Due date is required")
    LocalDate dueDate,

    TransmittalStatus status,

    String remarks
) {
}
