package com.bipros.api.dto;

import java.util.List;

/**
 * Response from the admin activity-status FORCE endpoint. Reuses
 * {@link ActivityStatusCorrectionResponse.Result} for the per-activity rows.
 */
public record ActivityStatusForceResponse(
    boolean dryRun,
    Summary summary,
    List<ActivityStatusCorrectionResponse.Result> results
) {
    /** Counts of force outcomes. */
    public record Summary(
        int forced,
        int skipped
    ) {}
}
