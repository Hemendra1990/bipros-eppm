package com.bipros.project.application.dto;

import com.bipros.project.domain.model.HseIncidentType;
import com.bipros.project.domain.model.IssueCategory;
import com.bipros.project.domain.model.IssueSeverity;
import com.bipros.project.domain.model.IssueStatus;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Partial-update request for the dedicated issue PATCH endpoint. All fields are nullable; only
 * non-null fields are applied. The service auto-manages {@code resolvedAt} on status transitions
 * (set to {@code now()} on entering RESOLVED/CLOSED, cleared on leaving them).
 *
 * <p>Immutable fields deliberately absent: {@code dprId}, {@code projectId}, {@code reportDate},
 * {@code openedAt}, {@code chainage}. Activity is patchable for standalone issues (dprId=null);
 * for DPR-bound issues the activity snapshot is preserved by service convention.
 */
public record UpdateDprIssueRequest(
    @Size(max = 150) String title,
    @Size(max = 2000) String description,
    IssueCategory category,
    IssueSeverity severity,
    IssueStatus status,
    UUID supervisorResourceId,
    String supervisorName,
    UUID assignedToResourceId,
    String assignedToName,
    @Size(max = 1000) String resolutionNotes,
    UUID supervisorUserId,
    UUID assignedToUserId,
    UUID activityId,
    @Size(max = 150) String activityName,
    /** Optional free-text reason recorded on the status-change history row (non-terminal moves). */
    @Size(max = 1000) String statusChangeReason,
    /**
     * Optional HSE sub-classification; only set for SAFETY/ENVIRONMENTAL issues.
     *
     * <p><strong>EXCEPTION to the "non-null fields only" rule:</strong> this field is applied
     * UNCONDITIONALLY by {@code DprIssueService.patch} — a {@code null} value explicitly clears
     * the classification (required for the IssueForm re-classify / clear flow). Any partial-PATCH
     * caller (e.g. a quick status-change) MUST include this field with the row's existing value so
     * it is not inadvertently wiped.
     */
    HseIncidentType hseIncidentType
) {
}
