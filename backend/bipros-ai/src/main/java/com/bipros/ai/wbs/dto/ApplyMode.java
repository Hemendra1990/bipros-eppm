package com.bipros.ai.wbs.dto;

/**
 * How AI-generated WBS nodes are merged into the project at apply time.
 *
 * <ul>
 *   <li>{@link #MERGE} (default): graft new nodes alongside existing ones.
 *       Duplicates by (code, name) are skipped; same-code-different-name pairs
 *       are renamed with an {@code -AI} suffix; nodes whose {@code parentCode}
 *       points at an existing node land under that parent.</li>
 *   <li>{@link #ADD_UNDER}: like MERGE, but every root-level generated node is
 *       forced under {@code WbsAiApplyRequest.parentId} regardless of any
 *       {@code parentCode} the model proposed. Useful for "extract sub-packages
 *       under phase 5" workflows.</li>
 *   <li>{@link #REPLACE}: destructive. Deletes every WBS node in the project,
 *       then inserts the generated tree. Requires explicit confirmation in
 *       the UI before reaching the backend.</li>
 * </ul>
 */
public enum ApplyMode {
    MERGE,
    ADD_UNDER,
    REPLACE
}
