package com.bipros.common.event;

import java.util.UUID;

/**
 * Published by the Issues surfaces (standalone create / PATCH in {@code DprIssueService}) when
 * an issue's responsible person is set or changed — the trigger for the assignment
 * auto-notification (AI Agent sheet, Issues row: "assign the responsible person … and auto
 * email to be given to the related people"). Deliberately minimal: the AFTER_COMMIT listener
 * in bipros-api re-reads the issue for content. DPR-path issue saves do NOT publish this —
 * their assignee defaults to the filing supervisor, which is not an assignment.
 */
public record IssueAssignedEvent(
    UUID projectId,
    UUID issueId,
    UUID assignedToUserId
) {}
