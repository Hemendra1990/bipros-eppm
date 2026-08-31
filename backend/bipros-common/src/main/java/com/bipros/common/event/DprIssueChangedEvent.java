package com.bipros.common.event;

import java.util.UUID;

/**
 * Published after a single DPR issue is patched / deleted outside the parent DPR save flow
 * (i.e. via {@code DprIssueController.PATCH/DELETE}). Kept separate from
 * {@link DprSubmittedEvent} because the parent DPR's qty / BOQ hasn't changed — only the issue
 * row — so {@code DprBoqSyncListener} should not fire. Analytics listeners react to push a
 * single-row upsert into {@code fact_dpr_issues_daily}.
 *
 * <p>{@code oldStatus} is {@code null} for CREATED-via-PATCH (not currently supported —
 * creation routes through the parent DPR) and for DELETED events.
 */
public record DprIssueChangedEvent(
    UUID projectId,
    UUID dprId,
    UUID issueId,
    String oldStatus,
    String newStatus,
    DprMutationType eventType
) {
}
