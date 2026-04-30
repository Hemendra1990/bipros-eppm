package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadDprSummaryTool extends ProjectScopedTool {

    private final ClickHouseTemplate clickHouse;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "read_dpr_summary";
    }

    @Override
    public String description() {
        return "Read Daily Progress Report summary: per-day rollup of qty executed, chainage, weather, supervisor, and remarks excerpt.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.put("days", objectMapper.createObjectNode().put("type", "integer").put("default", 7));
        props.put("activity_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid"));
        schema.set("properties", props);
        return schema;
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        int days = input.path("days").asInt(7);
        String activityIdStr = input.path("activity_id").asText(null);
        UUID activityId = activityIdStr != null ? UUID.fromString(activityIdStr) : null;

        LocalDate from = LocalDate.now().minusDays(days);
        LocalDate to = LocalDate.now();

        StringBuilder sql = new StringBuilder("""
            SELECT report_date, activity_id, sum(qty_executed) as qty, sum(cumulative_qty) as cumulative,
                   anyLast(supervisor_name) as supervisor, anyLast(weather) as weather,
                   anyLast(remarks_text) as remarks
            FROM bipros_analytics.fact_dpr_logs
            WHERE project_id = :projectId AND report_date BETWEEN :from AND :to
            """);
        if (activityId != null) {
            sql.append(" AND activity_id = :activityId");
        }
        sql.append(" GROUP BY report_date, activity_id ORDER BY report_date DESC");

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", ctx.projectId());
        params.put("from", from);
        params.put("to", to);
        if (activityId != null) {
            params.put("activityId", activityId);
        }

        List<Map<String, Object>> rows = clickHouse.queryForList(sql.toString(), params);
        ArrayNode arr = objectMapper.createArrayNode();
        for (Map<String, Object> r : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            r.forEach((k, v) -> o.set(k, objectMapper.valueToTree(v)));
            arr.add(o);
        }

        return ToolResult.table("DPR summary last " + days + " days", arr,
                new String[]{"report_date", "activity_id", "qty", "cumulative", "supervisor", "weather", "remarks"});
    }
}
