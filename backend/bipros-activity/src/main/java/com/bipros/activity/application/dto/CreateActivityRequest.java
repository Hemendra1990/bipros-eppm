package com.bipros.activity.application.dto;

import com.bipros.activity.domain.model.ActivityType;
import com.bipros.activity.domain.model.DurationType;
import com.bipros.activity.domain.model.PercentCompleteType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

public record CreateActivityRequest(
    @NotBlank(message = "Code is required")
    String code,

    @NotBlank(message = "Name is required")
    String name,

    String description,

    @NotNull(message = "Project ID is required")
    UUID projectId,

    @NotNull(message = "WBS Node ID is required")
    UUID wbsNodeId,

    ActivityType activityType,

    DurationType durationType,

    PercentCompleteType percentCompleteType,

    @PositiveOrZero(message = "Original duration must be zero or positive")
    Double originalDuration,

    LocalDate plannedStartDate,

    LocalDate plannedFinishDate,

    UUID calendarId,

    Long chainageFromM,

    Long chainageToM,

    /**
     * Soft FK to {@code resource.work_activities.id}. Optional — when present, links this
     * project activity to its master/library entry so productivity norms can be resolved
     * per (activity, deployed resource).
     */
    UUID workActivityId,

    /**
     * Soft FK to {@code cost.cost_accounts.id}. Optional — when present, assigns the activity to
     * a cost account directly, overriding any cost account inherited from the WBS node.
     */
    UUID costAccountId,

    /**
     * Soft FK to {@code resource.resources.id} — the LABOR Resource accountable for this activity
     * as supervisor. The frontend filters the picker to LABOR resources from the project pool.
     * Stored on {@code Activity.responsibleResourceId} so the activity grid + DPR pre-fill can
     * resolve without a cross-module fetch.
     */
    UUID supervisorResourceId,

    /**
     * Display-snapshot of the supervisor Resource's name at the moment of assignment. Frontend
     * passes this from the picker option label so the backend doesn't need a cross-module
     * lookup. Goes stale if the Resource is later renamed — accepted limitation.
     */
    String supervisorResourceName
) {}
