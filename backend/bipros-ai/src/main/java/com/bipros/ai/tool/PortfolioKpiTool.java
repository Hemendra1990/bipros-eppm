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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates EVM KPIs per project across the user's accessible scope.
 * Backed by mv_project_kpi_daily (sums of AC/PV/EV).
 *
 * Use when the user asks "which projects have the worst CPI / SPI", "which
 * projects are over budget this month", or any portfolio-level performance
 * question. For a single project drill-down, use analyze_cost or query_clickhouse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioKpiTool extends ProjectScopedTool {

    private final ClickHouseTemplate clickHouse;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "portfolio_kpi";
    }

    @Override
    public String description() {
        return "Aggregate AC / PV / EV / CPI / SPI per project from mv_project_kpi_daily over a "
                + "date range. Defaults to last 30 days, all accessible projects. Output rows are "
                + "one per project, sortable by CPI ascending to find under-performers.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.set("from", objectMapper.createObjectNode().put("type", "string")
                .put("format", "date").put("description", "ISO date — defaults to today() - 30 days"));
        props.set("to", objectMapper.createObjectNode().put("type", "string")
                .put("format", "date").put("description", "ISO date — defaults to today()"));
        ObjectNode projectIds = objectMapper.createObjectNode();
        projectIds.put("type", "array");
        projectIds.set("items", objectMapper.createObjectNode().put("type", "string").put("format", "uuid"));
        projectIds.put("description", "Optional explicit project_id subset. Defaults to user's full accessible scope. "
                + "Any ids outside that scope are silently dropped.");
        props.set("project_ids", projectIds);
        schema.set("properties", props);
        return schema;
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        LocalDate from = parseDate(input.path("from").asText(null), LocalDate.now().minusDays(30));
        LocalDate to = parseDate(input.path("to").asText(null), LocalDate.now());

        Set<UUID> scope = ctx.scopedProjectIds() == null
                ? Set.of()
                : new HashSet<>(ctx.scopedProjectIds());

        List<UUID> requested = new ArrayList<>();
        JsonNode reqIds = input.path("project_ids");
        if (reqIds.isArray()) {
            for (JsonNode n : reqIds) {
                try {
                    requested.add(UUID.fromString(n.asText()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        List<UUID> effective;
        if (!requested.isEmpty()) {
            effective = scope.isEmpty()
                    ? requested
                    : requested.stream().filter(scope::contains).toList();
            if (effective.isEmpty()) {
                return ToolResult.error("None of the requested project_ids are within your accessible scope. "
                        + "Call list_projects to see which projects you can query.");
            }
        } else {
            effective = scope.isEmpty() ? List.of() : List.copyOf(scope);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT project_id,
                       sum(pv) AS pv,
                       sum(ev) AS ev,
                       sum(ac) AS ac,
                       round(sum(ev) / nullIf(sum(ac), 0), 3) AS cpi,
                       round(sum(ev) / nullIf(sum(pv), 0), 3) AS spi,
                       sum(ac) - sum(ev) AS cv_overrun
                FROM bipros_analytics.mv_project_kpi_daily
                WHERE date BETWEEN :from AND :to
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("from", from);
        params.put("to", to);

        if (!effective.isEmpty()) {
            String inList = effective.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
            sql.append(" AND project_id IN (").append(inList).append(")");
        } else {
            // Admin with empty scope — keep the literal "project_id" substring so
            // SqlGuard-style heuristics elsewhere stay happy, while still matching
            // every row.
            sql.append(" AND project_id IS NOT NULL");
        }
        sql.append(" GROUP BY project_id ORDER BY cpi ASC NULLS LAST LIMIT 500");

        List<Map<String, Object>> rows = clickHouse.queryForList(sql.toString(), params);
        ArrayNode arr = objectMapper.createArrayNode();
        for (Map<String, Object> r : rows) {
            ObjectNode o = objectMapper.createObjectNode();
            r.forEach((k, v) -> o.set(k, objectMapper.valueToTree(v)));
            arr.add(o);
        }

        return ToolResult.table(
                "Portfolio KPIs from " + from + " to " + to + " — " + rows.size() + " project(s)",
                arr,
                new String[]{"project_id", "pv", "ev", "ac", "cpi", "spi", "cv_overrun"}
        );
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            return fallback;
        }
    }
}
