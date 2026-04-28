package com.bipros.analytics.application.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Contract every analytics tool implements. Combined with the {@link AnalyticsTool}
 * annotation on the implementing class, the {@link ToolRegistry} picks up handlers
 * automatically at boot.
 *
 * @param <REQ> request DTO type the tool deserializes its arguments into
 */
public interface AnalyticsToolHandler<REQ> {

    /** Tool name (must match the @AnalyticsTool annotation). */
    String name();

    /** Type used by {@link ToolDispatcher} to deserialize arguments JSON. */
    Class<REQ> requestType();

    /** JSON Schema describing the inputs (sent to the LLM in the system prompt). */
    JsonNode inputSchema();

    /** Execute against ClickHouse and return a result. */
    ToolResult execute(REQ req, AuthContext auth);
}
