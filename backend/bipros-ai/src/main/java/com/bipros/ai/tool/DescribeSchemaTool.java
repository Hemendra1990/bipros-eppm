package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Returns the ClickHouse schema description so the agent can re-confirm
 * column names / table layouts mid-loop without re-reading the system prompt.
 */
@Component
@RequiredArgsConstructor
public class DescribeSchemaTool implements Tool {

    private final SchemaCatalog catalog;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "describe_schema";
    }

    @Override
    public String description() {
        return "Return the ClickHouse analytics schema (tables and columns). "
                + "Optional `table` argument narrows the result to a single table.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("table", objectMapper.createObjectNode().put("type", "string")
                .put("description", "Optional table name (e.g. fact_evm_daily). Omit to list all tables."));
        schema.set("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input, AiContext ctx) {
        String table = input.path("table").asText(null);
        String content = (table == null || table.isBlank()) ? catalog.full() : catalog.forTable(table);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("schema", content);
        return ToolResult.ok((table == null || table.isBlank())
                ? "ClickHouse analytics schema"
                : "Schema for " + table, payload);
    }
}
