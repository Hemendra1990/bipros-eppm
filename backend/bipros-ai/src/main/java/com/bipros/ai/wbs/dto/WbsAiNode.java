package com.bipros.ai.wbs.dto;

import java.util.List;

/**
 * One node in the AI-extracted WBS tree.
 *
 * <p>{@code parentCode} is optional and lets the model reference an existing
 * project node as parent for newly-extracted children, instead of nesting a
 * duplicate. When set, the node is grafted under the existing parent at apply
 * time; when null, it is a root-level node (or a child of another generated
 * node via the recursive {@code children} field).
 */
public record WbsAiNode(
        String code,
        String name,
        String description,
        String parentCode,
        List<WbsAiNode> children
) {
    /** Convenience for callers that don't need parentCode (back-compat). */
    public WbsAiNode(String code, String name, String description, List<WbsAiNode> children) {
        this(code, name, description, null, children);
    }
}
