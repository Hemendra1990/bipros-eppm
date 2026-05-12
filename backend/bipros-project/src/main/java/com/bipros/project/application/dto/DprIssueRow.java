package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DprIssue;
import com.bipros.project.domain.model.IssueCategory;
import com.bipros.project.domain.model.IssueSeverity;
import com.bipros.project.domain.model.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Field-issue row attached to a DPR. {@code id} is {@code null} on insert and non-null on
 * update — the parent DPR's save uses merge-by-id semantics: rows in the database that are
 * <i>absent</i> from the submitted list are deleted, rows with a known id are updated in
 * place (preserving {@code openedAt} / lifecycle), and rows without an id are inserted with
 * snapshots stamped from the parent DPR.
 *
 * <p>{@code openedAt} / {@code resolvedAt} are response-only. The service auto-manages
 * {@code resolvedAt} based on {@code status} transitions: setting status to {@code RESOLVED}
 * or {@code CLOSED} stamps now(); transitioning back clears it. Clients must not set
 * {@code resolvedAt} directly.
 *
 * <p>Sending {@code null} <i>or</i> empty {@code []} for the parent's {@code issues} list
 * means "clear all issues for this DPR". There is no "leave alone" sentinel — clients round-
 * trip the latest server payload.
 */
public record DprIssueRow(
    UUID id,
    @NotBlank @Size(max = 150) String title,
    @Size(max = 2000) String description,
    @NotNull IssueCategory category,
    @NotNull IssueSeverity severity,
    @NotNull IssueStatus status,
    UUID supervisorResourceId,
    String supervisorName,
    UUID assignedToResourceId,
    String assignedToName,
    Instant openedAt,
    Instant resolvedAt,
    @Size(max = 1000) String resolutionNotes
) {
    public static DprIssueRow from(DprIssue e) {
        return new DprIssueRow(
            e.getId(),
            e.getTitle(),
            e.getDescription(),
            e.getCategory(),
            e.getSeverity(),
            e.getStatus(),
            e.getSupervisorResourceId(),
            e.getSupervisorName(),
            e.getAssignedToResourceId(),
            e.getAssignedToName(),
            e.getOpenedAt(),
            e.getResolvedAt(),
            e.getResolutionNotes());
    }
}
