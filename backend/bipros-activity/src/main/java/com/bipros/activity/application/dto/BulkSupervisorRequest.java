package com.bipros.activity.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Bulk-assigns one supervisor (a Resource) across many activities. The frontend
 * filters the picker to LABOR/Manpower resources from the project pool; the backend
 * trusts that filtering and just stores the id + denormalised name on each activity's
 * {@code responsibleResourceId} / {@code responsibleResourceName} columns.
 */
public record BulkSupervisorRequest(
    @NotNull(message = "supervisorResourceId is required")
    UUID supervisorResourceId,

    /** Display-snapshot name from the picker option label. Goes stale if the Resource
     * is renamed later — accepted limitation, mirrors the per-activity supervisor field. */
    String supervisorResourceName,

    @NotEmpty(message = "activityIds must contain at least one activity")
    List<UUID> activityIds
) {}
