package com.bipros.analytics.application.tool.impl;

import com.bipros.analytics.application.tool.AnalyticsTool;
import com.bipros.analytics.application.tool.AnalyticsToolHandler;
import com.bipros.analytics.application.tool.AuthContext;
import com.bipros.analytics.application.tool.ToolResult;
import com.bipros.security.application.service.ProjectAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time series of EVM snapshots for a project, optionally narrowed to a WBS node, an
 * activity, or a date range. Sorted by snapshot date ascending; capped at 500 rows.
 */
@AnalyticsTool(name = "get_evm_snapshot",
        description = "EVM time series (PV/EV/AC/SPI/CPI) for a project, optionally scoped to a WBS or activity.")
@Slf4j
public class GetEvmSnapshotTool implements AnalyticsToolHandler<GetEvmSnapshotTool.Req> {

    private final ObjectMapper objectMapper;
    private final ProjectAccessService projectAccessService;
    private final JdbcTemplate ch;

    public GetEvmSnapshotTool(ObjectMapper objectMapper,
                              ProjectAccessService projectAccessService,
                              @Qualifier("clickhouseReaderJdbcTemplate") JdbcTemplate ch) {
        this.objectMapper = objectMapper;
        this.projectAccessService = projectAccessService;
        this.ch = ch;
    }

    public record Req(UUID project_id, UUID wbs_node_id, UUID activity_id,
                      LocalDate from_date, LocalDate to_date) {}

    @Override public String name() { return "get_evm_snapshot"; }
    @Override public Class<Req> requestType() { return Req.class; }

    @Override
    public JsonNode inputSchema() {
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_id", Map.of("type", "string", "format", "uuid"),
                        "wbs_node_id", Map.of("type", "string", "format", "uuid",
                                "description", "Optional. Narrow to a single WBS node."),
                        "activity_id", Map.of("type", "string", "format", "uuid",
                                "description", "Optional. Narrow to a single activity."),
                        "from_date", Map.of("type", "string", "format", "date",
                                "description", "Optional inclusive lower bound."),
                        "to_date", Map.of("type", "string", "format", "date",
                                "description", "Optional inclusive upper bound.")
                ),
                "required", List.of("project_id"),
                "additionalProperties", false
        ));
    }

    @Override
    public ToolResult execute(Req req, AuthContext auth) {
        if (req == null || req.project_id == null) {
            throw new IllegalArgumentException("project_id is required");
        }
        projectAccessService.requireRead(req.project_id);

        StringBuilder sql = new StringBuilder(
                "SELECT data_date, wbs_node_id, activity_id, planned_value, earned_value, "
                        + "actual_cost, schedule_performance_index, cost_performance_index, "
                        + "schedule_variance, cost_variance "
                        + "FROM bipros_analytics.fact_evm_snapshots FINAL WHERE project_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(req.project_id.toString());

        if (req.wbs_node_id != null) {
            sql.append(" AND wbs_node_id = ?");
            args.add(req.wbs_node_id.toString());
        }
        if (req.activity_id != null) {
            sql.append(" AND activity_id = ?");
            args.add(req.activity_id.toString());
        }
        if (req.from_date != null) {
            sql.append(" AND data_date >= ?");
            args.add(java.sql.Date.valueOf(req.from_date));
        }
        if (req.to_date != null) {
            sql.append(" AND data_date <= ?");
            args.add(java.sql.Date.valueOf(req.to_date));
        }
        sql.append(" ORDER BY data_date ASC LIMIT 500");

        List<Map<String, Object>> rows = ch.queryForList(sql.toString(), args.toArray());
        List<String> cols = List.of("data_date", "wbs_node_id", "activity_id",
                "planned_value", "earned_value", "actual_cost",
                "schedule_performance_index", "cost_performance_index",
                "schedule_variance", "cost_variance");
        String narrative = rows.isEmpty()
                ? "No EVM snapshots found for the requested scope."
                : rows.size() + " EVM snapshot(s) returned.";
        return new ToolResult(narrative, cols, rows, null);
    }
}
