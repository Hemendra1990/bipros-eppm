package com.bipros.ai.wbs.dto;

/**
 * Per-node outcome from the apply step. Returned to the frontend so the
 * preview can label each tree row with what actually happened (or, for a
 * dry-run, what would happen).
 */
public record CollisionResult(
        String originalCode,
        String resolvedCode,
        CollisionAction action,
        String reason
) {
    public enum CollisionAction {
        /** Existing node has the same code AND the same name — no insert. */
        SKIPPED_DUPLICATE,
        /** Code collided; node was inserted with an {@code -AI} suffix. */
        RENAMED,
        /** {@code parentCode} resolved to an existing project node. */
        RESOLVED_TO_EXISTING_PARENT,
        /** Plain insert; no collision. */
        INSERTED_NEW,
        /**
         * Activity-only: the activity references a {@code wbsNodeCode} that does
         * not exist in the project. On apply (non-strict mode) it is silently
         * skipped; on strict mode the whole apply aborts. The {@code reason}
         * field carries operator-readable detail.
         */
        MISSING_WBS_NODE,
        /**
         * Activity-only: {@code wbsNodeCode} did not exact-match any existing
         * code, but a close match (case-insensitive equality or Levenshtein ≤2)
         * exists. {@code resolvedCode} carries the suggested target; the user
         * can accept the suggestion in the preview to populate the apply-time
         * {@code wbsRemap}.
         */
        WBS_NEAR_MATCH
    }
}
