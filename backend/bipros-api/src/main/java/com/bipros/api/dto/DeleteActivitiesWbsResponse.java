package com.bipros.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response from the admin delete-activities-and-WBS endpoint: the resolved target set, the counts
 * that WOULD/DID delete, and blockers that caused (or would cause) an abort.
 */
public record DeleteActivitiesWbsResponse(
    boolean dryRun,
    boolean aborted,
    List<String> abortReasons,
    Resolved resolved,
    WillDelete willDelete,
    Blockers blockers
) {
    /** The fully-resolved target set (echoed for the audit trail). */
    public record Resolved(
        List<UUID> wbsNodeIds,
        List<UUID> activityIds
    ) {}

    /** Counts that would be deleted (dry-run) or were deleted (apply). */
    public record WillDelete(
        int wbsNodes,
        int activities,
        int resourceAssignments,
        int scheduleResults,
        int codeAssignments,
        int relationshipsInTarget,
        int supervisors
    ) {}

    /** Items that block (or, with force, are handled specially). */
    public record Blockers(
        List<ActivityDpr> activitiesWithDprs,
        List<RelationshipToKept> relationshipsToKeptActivities,
        List<BoqMapped> boqItemsMappedToDeletedWbs,
        List<UUID> lockedActivities,
        List<UnexpectedChild> unexpectedChildData
    ) {}

    public record ActivityDpr(UUID activityId, String code, long dprCount) {}
    public record RelationshipToKept(UUID relationshipId, UUID keptActivityId) {}
    public record BoqMapped(UUID boqItemId, String itemNo, UUID wbsNodeId) {}
    public record UnexpectedChild(UUID activityId, String table, long count) {}
}
