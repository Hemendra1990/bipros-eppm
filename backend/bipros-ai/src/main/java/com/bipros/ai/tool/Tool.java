package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/**
 * A tool callable by the AI orchestrator.
 *
 * <p>{@link #allowedRoles()} returns the set of profile codes (e.g.
 * {@code "PROJECT_MANAGER"}) for which this tool is visible. An empty set
 * means "visible to every profile". {@code SYSTEM_ADMIN} is implicitly
 * allowed everywhere — handled at the registry level, NOT in each tool.
 */
public interface Tool {

    String name();

    String description();

    JsonNode inputSchema();

    ToolResult execute(JsonNode input, AiContext ctx);

    default boolean isReadOnly() {
        return true;
    }

    default Set<String> allowedRoles() {
        return Set.of();
    }
}
