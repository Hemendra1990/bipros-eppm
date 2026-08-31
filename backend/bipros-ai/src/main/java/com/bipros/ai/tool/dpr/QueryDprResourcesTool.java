package com.bipros.ai.tool.dpr;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resource-level Daily Progress Report query — what manpower trades, PMV equipment, or materials
 * were deployed across DPRs. Hits the per-resource ClickHouse fact tables added in 2026-Q2:
 * {@code fact_dpr_manpower_daily}, {@code fact_dpr_equipment_daily}, {@code fact_dpr_material_daily}.
 *
 * <p>Use this tool when the user asks who/what was on site, fleet utilization, fuel burn, trade
 * mix, or material sourcing — anything that drills under a DPR row into individual resource lines.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryDprResourcesTool implements Tool {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final ClickHouseTemplate clickHouse;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "query_dpr_resources";
    }

    @Override
    public String description() {
        return "Query the per-resource breakdown of Daily Progress Reports — what trades, "
                + "equipment fleets, or materials were deployed for each activity each day. "
                + "Pick `resource_kind` to choose: manpower (trades, head count, hours), "
                + "equipment (fleet numbers, working/idle/breakdown hours, fuel), or material "
                + "(consumption, source, vendor, batch). Filter by date range, activity (code OR id), "
                + "and resource-specific fields (trade name, equipment_type, material_name). "
                + "Use group_by to roll up by date / activity / resource. Examples: "
                + "\"What equipment ran on activity 2.3.6(i) on 24 Jan 2026?\", "
                + "\"How many helpers worked yesterday at chainage 4+300?\", "
                + "\"Fuel consumption by Excavator over the last week\", "
                + "\"Aggregate manpower hours by trade for March\". Requires a current project in scope. "
                + "NOTE: warehouse fact tables do not carry rate basis or pool-override metadata; "
                + "for cost-precise per-row queries (unit_rate_basis, cost_formula, rate drift) "
                + "use get_dpr_details instead.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("resource_kind", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description",
                        "Required. One of: 'manpower', 'equipment', 'material'. Picks which fact table to query."));
        props.set("date_from", objectMapper.createObjectNode()
                .put("type", "string").put("format", "date")
                .put("description", "ISO date (YYYY-MM-DD). Default: 30 days before date_to."));
        props.set("date_to", objectMapper.createObjectNode()
                .put("type", "string").put("format", "date")
                .put("description", "ISO date (YYYY-MM-DD). Default: today."));
        props.set("activity_code", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "Activity short code (e.g. ACT-1.3.5(ii) or 2.3.6(i))."));
        props.set("activity_id", objectMapper.createObjectNode()
                .put("type", "string").put("format", "uuid")
                .put("description", "Activity UUID."));
        props.set("trade", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "Manpower-only: substring filter on trade (e.g. 'Helper', 'Operator')."));
        props.set("equipment_type", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "Equipment-only: substring filter on equipment_type (e.g. 'Excavator')."));
        props.set("fleet_no", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "Equipment-only: exact fleet number (e.g. 'Exc-38')."));
        props.set("material_name", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "Material-only: substring filter on material_name."));
        props.set("group_by", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "One of: 'date', 'activity', 'resource', 'none'. Default 'none' (raw rows)."));
        props.set("limit", objectMapper.createObjectNode()
                .put("type", "integer")
                .put("description", "Max rows to return. Default 100, max 500."));
        schema.set("properties", props);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("resource_kind");
        schema.set("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input, AiContext ctx) {
        UUID projectId = ctx.projectId();
        if (projectId == null) {
            return ToolResult.error("No current project in scope. Call list_projects first.");
        }

        String kind = orNull(input.path("resource_kind").asText(null));
        if (kind == null) {
            return ToolResult.error("resource_kind is required (one of: manpower, equipment, material).");
        }
        kind = kind.trim().toLowerCase();
        if (!kind.equals("manpower") && !kind.equals("equipment") && !kind.equals("material")) {
            return ToolResult.error("resource_kind must be one of: manpower, equipment, material.");
        }

        LocalDate dateTo = parseDate(input.path("date_to").asText(null), LocalDate.now());
        LocalDate dateFrom = parseDate(input.path("date_from").asText(null), dateTo.minusDays(30));
        if (dateFrom.isAfter(dateTo)) {
            return ToolResult.error("date_from must be on or before date_to.");
        }

        int limit = clamp(input.path("limit").asInt(DEFAULT_LIMIT), 1, MAX_LIMIT);
        String groupBy = orNull(input.path("group_by").asText(null));
        if (groupBy != null) groupBy = groupBy.trim().toLowerCase();

        UUID activityId = resolveActivityId(input, projectId);

        String table = switch (kind) {
            case "manpower" -> "bipros_analytics.fact_dpr_manpower_daily";
            case "equipment" -> "bipros_analytics.fact_dpr_equipment_daily";
            default -> "bipros_analytics.fact_dpr_material_daily";
        };

        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("dateFrom", dateFrom);
        params.put("dateTo", dateTo);
        params.put("limit", limit);

        if ("date".equals(groupBy)) {
            sql.append(switch (kind) {
                case "manpower" -> "SELECT report_date AS bucket, sum(nos) AS total_nos, "
                        + "sum(working_hours) AS total_hours, sum(ot_hours) AS total_ot, "
                        + "uniqExact(trade) AS distinct_trades ";
                case "equipment" -> "SELECT report_date AS bucket, sum(nos) AS total_units, "
                        + "sum(working_hours) AS total_working_hours, sum(idle_hours) AS total_idle_hours, "
                        + "sum(fuel_litres) AS total_fuel_litres, uniqExact(fleet_no) AS distinct_fleets ";
                default -> "SELECT report_date AS bucket, sum(quantity) AS total_qty, "
                        + "uniqExact(material_name) AS distinct_materials ";
            });
        } else if ("resource".equals(groupBy)) {
            sql.append(switch (kind) {
                case "manpower" -> "SELECT trade AS bucket, sum(nos) AS total_nos, "
                        + "sum(working_hours) AS total_hours ";
                case "equipment" -> "SELECT equipment_type AS bucket, sum(working_hours) AS total_hours, "
                        + "sum(idle_hours) AS total_idle_hours, sum(fuel_litres) AS total_fuel_litres, "
                        + "uniqExact(fleet_no) AS distinct_fleets ";
                default -> "SELECT material_name AS bucket, sum(quantity) AS total_qty, "
                        + "any(unit) AS unit ";
            });
        } else if ("activity".equals(groupBy)) {
            sql.append(switch (kind) {
                case "manpower" -> "SELECT activity_id AS bucket, sum(nos) AS total_nos, "
                        + "sum(working_hours) AS total_hours ";
                case "equipment" -> "SELECT activity_id AS bucket, sum(working_hours) AS total_hours, "
                        + "sum(fuel_litres) AS total_fuel_litres ";
                default -> "SELECT activity_id AS bucket, sum(quantity) AS total_qty ";
            });
        } else {
            sql.append("SELECT * ");
        }

        sql.append("FROM ").append(table).append(" FINAL ")
                .append("WHERE project_id = :projectId ")
                .append("AND report_date BETWEEN :dateFrom AND :dateTo ");

        if (activityId != null) {
            sql.append("AND activity_id = :activityId ");
            params.put("activityId", activityId);
        }

        if (kind.equals("manpower")) {
            String trade = orNull(input.path("trade").asText(null));
            if (trade != null) {
                sql.append("AND positionCaseInsensitive(trade, :trade) > 0 ");
                params.put("trade", trade);
            }
        } else if (kind.equals("equipment")) {
            String type = orNull(input.path("equipment_type").asText(null));
            if (type != null) {
                sql.append("AND positionCaseInsensitive(equipment_type, :equipmentType) > 0 ");
                params.put("equipmentType", type);
            }
            String fleetNo = orNull(input.path("fleet_no").asText(null));
            if (fleetNo != null) {
                sql.append("AND fleet_no = :fleetNo ");
                params.put("fleetNo", fleetNo);
            }
        } else {
            String mat = orNull(input.path("material_name").asText(null));
            if (mat != null) {
                sql.append("AND positionCaseInsensitive(material_name, :materialName) > 0 ");
                params.put("materialName", mat);
            }
        }

        if (groupBy != null && !groupBy.equals("none")) {
            sql.append("GROUP BY bucket ORDER BY bucket ");
        } else {
            sql.append("ORDER BY report_date DESC ");
        }
        sql.append("LIMIT :limit");

        try {
            List<Map<String, Object>> ch = clickHouse.queryForList(sql.toString(), params);
            ArrayNode rows = objectMapper.valueToTree(ch);

            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("resource_kind", kind);
            wrapper.put("date_from", dateFrom.toString());
            wrapper.put("date_to", dateTo.toString());
            wrapper.put("group_by", groupBy != null ? groupBy : "none");
            wrapper.put("row_count", rows.size());
            wrapper.set("rows", rows);
            // Rows come from the analytics warehouse, which does not carry rate basis or
            // pool-override metadata. For cost-precise answers route the user to
            // get_dpr_details (per-row unit_rate_basis, cost_formula, drift detection).
            ArrayNode notes = objectMapper.createArrayNode();
            notes.add("warehouse_snapshot_basis_blind");
            wrapper.set("formula_overrides", notes);

            String summary = String.format("query_dpr_resources(%s) returned %d rows over %s..%s",
                    kind, rows.size(), dateFrom, dateTo);
            return ToolResult.ok(summary, wrapper);
        } catch (DataAccessException e) {
            log.warn("query_dpr_resources failed: {}", e.getMessage());
            return ToolResult.error("ClickHouse query failed: " + e.getMessage());
        }
    }

    private UUID resolveActivityId(JsonNode input, UUID projectId) {
        String idStr = orNull(input.path("activity_id").asText(null));
        if (idStr != null) {
            try {
                return UUID.fromString(idStr);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        String code = orNull(input.path("activity_code").asText(null));
        if (code != null) {
            List<Activity> all = new ArrayList<>(activityRepository.findByProjectId(projectId));
            Optional<Activity> match = all.stream()
                    .filter(a -> a.getCode() != null && a.getCode().equalsIgnoreCase(code))
                    .findFirst();
            if (match.isPresent()) return match.get().getId();
            // also accept name match
            match = all.stream()
                    .filter(a -> a.getName() != null && a.getName().equalsIgnoreCase(code))
                    .findFirst();
            return match.map(Activity::getId).orElse(null);
        }
        return null;
    }

    private static LocalDate parseDate(String iso, LocalDate fallback) {
        if (iso == null || iso.isBlank()) return fallback;
        try {
            return LocalDate.parse(iso.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String orNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
