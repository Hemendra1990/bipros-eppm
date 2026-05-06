package com.bipros.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /**
     * Attaches drill-down hints to a wrapper {@link ObjectNode}. The orchestrator
     * surfaces these to the LLM under {@code linked_entity_ids} so the next tool
     * call can target a specific entity without an extra discovery round
     * (e.g. {@code {"activity": ["uuid"], "wbs": ["uuid"], "supervisor": ["uuid"]}}).
     * No-op if {@code wrapper} is null or {@code links} is empty.
     */
    public static void attachLinks(ObjectNode wrapper, Map<String, List<UUID>> links) {
        if (wrapper == null || links == null || links.isEmpty()) return;
        ObjectNode out = MAPPER.createObjectNode();
        for (Map.Entry<String, List<UUID>> e : links.entrySet()) {
            List<UUID> ids = e.getValue();
            if (ids == null || ids.isEmpty()) continue;
            ArrayNode arr = MAPPER.createArrayNode();
            for (UUID id : ids) {
                if (id != null) arr.add(id.toString());
            }
            if (!arr.isEmpty()) out.set(e.getKey(), arr);
        }
        if (!out.isEmpty()) wrapper.set("linked_entity_ids", out);
    }
}
