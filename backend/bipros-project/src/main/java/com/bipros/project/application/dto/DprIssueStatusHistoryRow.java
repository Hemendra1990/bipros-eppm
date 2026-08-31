package com.bipros.project.application.dto;

import com.bipros.project.domain.model.DprIssueStatusHistory;
import com.bipros.project.domain.model.IssueStatus;

import java.time.Instant;
import java.util.UUID;

/** Read model for one status transition in an issue's history timeline. */
public record DprIssueStatusHistoryRow(
    UUID id,
    IssueStatus fromStatus,
    IssueStatus toStatus,
    UUID actorUserId,
    String reason,
    Instant createdAt
) {
    public static DprIssueStatusHistoryRow from(DprIssueStatusHistory e) {
        return new DprIssueStatusHistoryRow(
            e.getId(), e.getFromStatus(), e.getToStatus(),
            e.getActorUserId(), e.getReason(), e.getCreatedAt());
    }
}
