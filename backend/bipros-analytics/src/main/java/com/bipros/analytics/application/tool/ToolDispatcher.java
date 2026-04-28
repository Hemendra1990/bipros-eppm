package com.bipros.analytics.application.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Looks up the registered handler by name, deserializes the LLM's argument JSON to the
 * handler's request type, and invokes it. Authorization is the handler's responsibility.
 */
@Component
@RequiredArgsConstructor
public class ToolDispatcher {

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ToolResult dispatch(String toolName, JsonNode argsJson, AuthContext auth) {
        AnalyticsToolHandler handler = registry.get(toolName);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        Object req = argsJson == null
                ? null
                : objectMapper.convertValue(argsJson, handler.requestType());
        return handler.execute(req, auth);
    }
}
