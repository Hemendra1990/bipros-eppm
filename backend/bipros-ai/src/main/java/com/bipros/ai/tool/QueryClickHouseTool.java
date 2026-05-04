package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.bipros.analytics.query.ClickHouseQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryClickHouseTool extends ProjectScopedTool {

    private final ClickHouseQueryService queryService;
    private final SchemaCatalog schemaCatalog;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "query_clickhouse";
    }

    @Override
    public String description() {
        return "Read-only SQL against the analytics warehouse. SELECT only; every query MUST include "
                + "a `project_id` filter (use IN for multi-project). Schema:\n\n" + schemaCatalog.full();
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.put("sql", new ObjectMapper().createObjectNode().put("type", "string"));
        props.put("row_limit", new ObjectMapper().createObjectNode().put("type", "integer").put("minimum", 1).put("maximum", 5000));
        schema.set("properties", props);
        ArrayNode req = objectMapper.createArrayNode();
        req.add("sql");
        schema.set("required", req);
        return schema;
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        String sql = input.path("sql").asText();
        int rowLimit = input.path("row_limit").asInt(500);
        try {
            ClickHouseQueryService.QueryResult result = queryService.runGuarded(sql, ctx.scopedProjectIds(), rowLimit);
            return ToolResult.table("Query returned " + result.rowCount() + " rows" +
                    (result.truncated() ? " (truncated)" : ""), result.rows(), new String[]{"rows"});
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.warn("query_clickhouse failed: {} (root cause: {}: {})",
                    e.getMessage(), root.getClass().getSimpleName(), root.getMessage(), e);
            return ToolResult.error(root.getMessage() != null ? root.getMessage() : e.getMessage());
        }
    }
}
