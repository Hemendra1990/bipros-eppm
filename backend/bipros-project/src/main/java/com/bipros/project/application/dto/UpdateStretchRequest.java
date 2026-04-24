package com.bipros.project.application.dto;

import com.bipros.project.domain.model.StretchStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** All fields optional. null leaves the current value untouched. */
public record UpdateStretchRequest(
    @Size(max = 200) String name,
    @Min(0) Long fromChainageM,
    @Min(0) Long toChainageM,
    UUID assignedSupervisorId,
    @Size(max = 60) String packageCode,
    StretchStatus status,
    @Size(max = 200) String milestoneName,
    LocalDate targetDate,
    List<UUID> boqItemIds
) {
}
