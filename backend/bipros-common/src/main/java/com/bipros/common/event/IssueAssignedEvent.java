package com.bipros.common.event;

import java.util.UUID;

/**
 * Published when an issue's responsible person is set or changed — the trigger for the
 * assignment auto-notification (AI Agent sheet, Issues row: "assign the responsible person …
 * and auto email to be given to the related people"). Deliberately minimal: the AFTER_COMMIT
 * listener in bipros-api re-reads the issue for content. Publishers: the standalone Issues
 * surfaces ({@code DprIssueService} create / PATCH) and, since the assignee picker moved to
 * the project team (owner decision 2026-08-31), the DPR save path
 * ({@code DailyProgressReportService.upsertIssues}) — the latter only for SUBMITTED parents
 * and never for a self-assignment, so a defaulted-to-the-filing-supervisor assignee stays
 * silent as before.
 */
public record IssueAssignedEvent(
    UUID projectId,
    UUID issueId,
    UUID assignedToUserId
) {}
