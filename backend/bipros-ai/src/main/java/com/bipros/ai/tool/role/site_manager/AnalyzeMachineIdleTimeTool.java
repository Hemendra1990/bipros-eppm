package com.bipros.ai.tool.role.site_manager;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.ProjectScopedTool;
import com.bipros.ai.tool.ToolResult;
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
import java.util.Set;

/**
 * Reports equipment with idle hours exceeding a threshold over a date range.
 *
 * <p>The underlying fact table is {@code bipros_analytics.fact_dpr_equipment_daily}, which is
 * populated by the ETL pipeline whenever a DPR equipment row is submitted. Columns used:
 * <ul>
 *   <li>{@code fleet_no} — unique equipment identifier (aliased as {@code equipment_id})</li>
 *   <li>{@code equipment_type} — equipment category/description (aliased as {@code equipment_name})</li>
 *   <li>{@code idle_hours} — hours the unit was idle that day</li>
 *   <li>{@code breakdown_hours} — hours spent in breakdown (proxy for breakdown reason where no
 *       free-text field exists; aliased as {@code breakdown_reason})</li>
 *   <li>{@code report_date} — the DPR date (aliased as {@code log_date})</li>
 * </ul>
 *
 * <p>Rows are grouped by {@code fleet_no} and {@code report_date} then filtered to those whose
 * summed idle hours meet or exceed the threshold. Results are ordered by idle hours descending so
 * the worst offenders appear first.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeMachineIdleTimeTool extends ProjectScopedTool {

    private final ClickHouseTemplate clickHouse;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "analyze_machine_idle_time";
    }

    @Override
    public String description() {
        return "List equipment with idle hours above a threshold over a date range, with the breakdown "
                + "reason where logged. Defaults: last 1 day, threshold 2 hours. Returns one row per "
                + "equipment-day above the threshold, ordered by idle hours descending.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("from", objectMapper.createObjectNode()
                .put("type", "string")
                .put("format", "date")
                .put("description", "Start date (inclusive). Defaults to 1 day before 'to'."));
        props.set("to", objectMapper.createObjectNode()
                .put("type", "string")
                .put("format", "date")
                .put("description", "End date (inclusive). Defaults to today."));
        props.set("threshold_hours", objectMapper.createObjectNode()
                .put("type", "number")
                .put("description", "Minimum idle hours per equipment-day to include. Defaults to 2."));
        schema.set("properties", props);
        return schema;
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("SITE_MANAGER", "PROJECT_MANAGER", "RESOURCE_MANAGER");
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        if (ctx.projectId() == null) {
            return ToolResult.error("Pick a project first — machine idle time is per-project.");
        }

        LocalDate to = parse(input.path("to").asText(null), LocalDate.now());
        LocalDate from = parse(input.path("from").asText(null), to.minusDays(1));
        double threshold = input.path("threshold_hours").asDouble(2.0);

        /*
         * fact_dpr_equipment_daily real columns (from ETL INSERT):
         *   project_id, activity_id, dpr_id, equipment_row_id,
         *   report_date, equipment_type, fleet_no, ownership,
         *   nos, working_hours, idle_hours, breakdown_hours,
         *   fuel_litres, operator_name, availability_status, ...
         *
         * We alias to the canonical output keys expected by callers:
         *   fleet_no          → equipment_id
         *   equipment_type    → equipment_name
         *   report_date       → log_date
         *   breakdown_hours   → breakdown_reason  (no free-text reason exists in the schema)
         *
         * Group by fleet_no + report_date, filter HAVING sum(idle_hours) >= :threshold,
         * order by idle hours descending.
         */
        String sql = """
                SELECT fleet_no                    AS equipment_id,
                       any(equipment_type)         AS equipment_name,
                       report_date                 AS log_date,
                       sum(idle_hours)             AS idle_hours,
                       sum(breakdown_hours)        AS breakdown_reason
                FROM bipros_analytics.fact_dpr_equipment_daily
                WHERE project_id = :pid
                  AND report_date BETWEEN :from AND :to
                GROUP BY fleet_no, report_date
                HAVING sum(idle_hours) >= :threshold
                ORDER BY idle_hours DESC
                LIMIT 200
                """;

        Map<String, Object> params = new HashMap<>();
        params.put("pid", ctx.projectId());
        params.put("from", from);
        params.put("to", to);
        params.put("threshold", threshold);

        List<Map<String, Object>> rows;
        try {
            rows = clickHouse.queryForList(sql, params);
        } catch (Exception e) {
            log.warn("analyze_machine_idle_time: ClickHouse query failed: {}", e.getMessage());
            return dataUnavailable(
                    "fact_dpr_equipment_daily is not yet populated or accessible for this project.",
                    "Backfill fact_dpr_equipment_daily from DPR equipment submissions for this project.",
                    "query_dpr (raw daily progress with equipment lines)");
        }

        if (rows.isEmpty()) {
            return dataUnavailable(
                    "No equipment rows above " + threshold + " idle hours for project "
                            + ctx.projectId() + " between " + from + " and " + to + ".",
                    "Confirm DPRs are being submitted with equipment entries for this period, "
                            + "or lower the threshold_hours parameter.",
                    "query_dpr to inspect raw DPR equipment records");
        }

        ArrayNode arr = objectMapper.createArrayNode();
        for (Map<String, Object> r : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            r.forEach((k, v) -> o.set(k, objectMapper.valueToTree(v)));
            arr.add(o);
        }

        return ToolResult.table(
                "Equipment idle time from " + from + " to " + to
                        + " (threshold " + threshold + " h) — " + rows.size() + " equipment-day(s).",
                arr,
                new String[]{"equipment_id", "equipment_name", "log_date", "idle_hours", "breakdown_reason"}
        );
    }

    private ToolResult dataUnavailable(String reason, String whatNeeded, String closest) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", "data_unavailable");
        payload.put("reason", reason);
        payload.put("what_would_be_needed", whatNeeded);
        payload.put("closest_available", closest);
        return ToolResult.ok("Data not yet captured: " + reason, payload);
    }

    private LocalDate parse(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            return fallback;
        }
    }
}
