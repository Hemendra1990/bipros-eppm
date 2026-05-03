package com.bipros.ai.activity.dto;

import com.bipros.ai.wbs.dto.CollisionResult;

import java.util.List;
import java.util.UUID;

/**
 * Detailed report of an activity-apply run.
 *
 * <p>{@code collisions} is the categorized per-activity outcome
 * (NEW / RENAMED / SKIPPED_DUPLICATE / RESOLVED_TO_EXISTING_PARENT*) used to
 * paint diff tags in the preview. The legacy {@code codeCollisions} string list
 * is kept for backwards compatibility with existing callers but mirrors the
 * RENAMED entries from {@code collisions}.
 *
 * <p>* RESOLVED_TO_EXISTING_PARENT is unused for activities (they do not have
 *   parent-pointer hierarchy) but the enum is shared with WBS; activities
 *   only emit NEW / RENAMED / SKIPPED_DUPLICATE / INSERTED_NEW.
 */
public record ActivityAiApplyResponse(
        List<CollisionResult> collisions,
        List<String> codeCollisions,
        List<String> wbsResolutionFailures,
        List<String> relationshipResolutionFailures,
        List<UUID> createdActivityIds,
        List<UUID> createdRelationshipIds
) {
    /** Back-compat constructor: callers without categorized collisions pass null. */
    public ActivityAiApplyResponse(
            List<String> codeCollisions,
            List<String> wbsResolutionFailures,
            List<String> relationshipResolutionFailures,
            List<UUID> createdActivityIds,
            List<UUID> createdRelationshipIds) {
        this(null, codeCollisions, wbsResolutionFailures, relationshipResolutionFailures,
                createdActivityIds, createdRelationshipIds);
    }
}
