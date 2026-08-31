package com.bipros.api.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request for the admin delete-activities-and-WBS endpoint. dryRun defaults to true (preview only).
 * wbsNodeIds = subtree roots to delete (node + all descendants + their activities); activityIds =
 * optional extra activities. force overrides only the recoverable blockers (stray schedule links,
 * BOQ unlink, locked). IRREVERSIBLE when dryRun=false.
 */
@Data
public class DeleteActivitiesWbsRequest {

    /** WBS subtree roots to delete (each: the node + all descendant WBS nodes + activities under them). */
    private List<UUID> wbsNodeIds;

    /** Optional explicit extra activities to delete. */
    private List<UUID> activityIds;

    /** Dry-run by default: compute + report, delete nothing. */
    private boolean dryRun = true;

    /** Override recoverable blockers: remove schedule links to kept activities, unlink BOQ items
     *  (null their wbsNodeId), and allow deleting locked activities. Never overrides DPR-present. */
    private boolean force = false;
}
