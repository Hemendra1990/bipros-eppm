package com.bipros.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Result of a tool execution.
 */
public record ToolResult(boolean success, String summary, JsonNode data, String error) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolResult ok(String summary, JsonNode data) {
        return new ToolResult(true, summary, data, null);
    }

    public static ToolResult ok(String summary) {
        return new ToolResult(true, summary, null, null);
    }

    public static ToolResult error(String error) {
        return new ToolResult(false, null, null, error);
    }

    public static ToolResult table(String summary, ArrayNode rows, String[] columns) {
        ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.set("rows", rows);
        ArrayNode cols = MAPPER.createArrayNode();
        for (String c : columns) cols.add(c);
        wrapper.set("columns", cols);
        return ok(summary, wrapper);
    }
}
