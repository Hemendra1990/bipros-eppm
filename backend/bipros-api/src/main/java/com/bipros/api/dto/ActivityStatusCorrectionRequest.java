package com.bipros.api.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request for the admin activity-status re-derivation endpoint.
 * dryRun defaults to true (preview only).
 */
@Data
public class ActivityStatusCorrectionRequest {

    /** The activities to re-derive. */
    private List<UUID> activityIds;

    /** Dry-run by default: compute + report, write nothing. */
    private boolean dryRun = true;
}
