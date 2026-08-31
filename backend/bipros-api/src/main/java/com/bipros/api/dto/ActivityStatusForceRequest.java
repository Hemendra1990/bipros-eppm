package com.bipros.api.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request for the admin activity-status FORCE endpoint: force each listed genuine-100%
 * activity down to IN_PROGRESS at a caller-supplied percent. dryRun defaults to true (preview).
 */
@Data
public class ActivityStatusForceRequest {

    /** The activities to force, each with its target in-progress percent. */
    private List<ForceTarget> activities;

    /** Dry-run by default: compute + report, write nothing. */
    private boolean dryRun = true;

    /** One activity + the percent it should read while IN_PROGRESS (must be 0 < x < 100). */
    @Data
    public static class ForceTarget {
        private UUID activityId;
        private Double targetPercent;
    }
}
