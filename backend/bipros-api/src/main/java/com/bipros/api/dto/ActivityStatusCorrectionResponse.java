package com.bipros.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response from the admin activity-status re-derivation endpoint.
 * Contains a summary of changes and detailed results per activity.
 */
public record ActivityStatusCorrectionResponse(
    boolean dryRun,
    Summary summary,
    List<Result> results
) {
    /**
     * Summary counts of status correction outcomes.
     */
    public record Summary(
        int resetNotStarted,
        int resetInProgress,
        int keptCompleted,
        int noBoqRecomputed,
        int skipped
    ) {}

    /**
     * Details of a single activity's status correction.
     */
    public record Result(
        UUID activityId,
        String code,
        String name,
        String oldStatus,
        Double oldPercent,
        String newStatus,
        Double newPercent,
        String outcome,   // RESET_NOT_STARTED | RESET_IN_PROGRESS | KEPT_COMPLETED | RESET_FROM_TYPE | SKIPPED_NO_BOQ_NO_RECOMPUTE | SKIPPED_NOT_FOUND | SKIPPED_WRONG_PROJECT
        String note       // nullable free-text (e.g. "was LOCKED")
    ) {}
}
