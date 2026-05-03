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
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "read_dpr_summary";
    }

    @Override
    public String description() {
        return "Daily Progress Report summary: per-day rollup of qty executed, chainage, weather, "
                + "supervisor and remarks. With a current project, groups by activity. With no current "
                + "project (general mode), groups by project_id across the user's accessible scope.";
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
        boolean crossProject = ctx.projectId() == null;

        StringBuilder sql = new StringBuilder();
        if (crossProject) {
            sql.append("""
                SELECT project_id, report_date,
                       sum(qty_executed) as qty,
                       count() as dpr_count,
                       countDistinct(activity_id) as activities,
                       anyLast(weather) as weather
                FROM bipros_analytics.fact_dpr_logs
                WHERE report_date BETWEEN :from AND :to
                """);
            List<UUID> scope = ctx.scopedProjectIds();
            if (scope != null && !scope.isEmpty()) {
                String inList = scope.stream().map(id -> "'" + id + "'")
                        .collect(java.util.stream.Collectors.joining(","));
                sql.append(" AND project_id IN (").append(inList).append(")");
            } else {
                sql.append(" AND project_id IS NOT NULL");
            }
            sql.append(" GROUP BY project_id, report_date ORDER BY report_date DESC, project_id LIMIT 500");
        } else {
            sql.append("""
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
        }

        Map<String, Object> params = new HashMap<>();
        params.put("from", from);
        params.put("to", to);
        if (!crossProject) {
            params.put("projectId", ctx.projectId());
            if (activityId != null) {
                params.put("activityId", activityId);
            }
        }

        List<Map<String, Object>> rows = clickHouse.queryForList(sql.toString(), params);
        ArrayNode arr = objectMapper.createArrayNode();
        for (Map<String, Object> r : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            r.forEach((k, v) -> o.set(k, objectMapper.valueToTree(v)));
            arr.add(o);
        }

        if (crossProject) {
            return ToolResult.table("DPR activity per project, last " + days + " days", arr,
                    new String[]{"project_id", "report_date", "qty", "dpr_count", "activities", "weather"});
        }
        return ToolResult.table("DPR summary last " + days + " days", arr,
                new String[]{"report_date", "activity_id", "qty", "cumulative", "supervisor", "weather", "remarks"});
    }
}
