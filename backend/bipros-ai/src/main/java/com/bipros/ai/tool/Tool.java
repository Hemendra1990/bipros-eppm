package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A tool callable by the AI orchestrator.
 */
public interface Tool {

    String name();

    String description();

    JsonNode inputSchema();

    ToolResult execute(JsonNode input, AiContext ctx);

    default boolean isReadOnly() {
        return true;
    }
}
