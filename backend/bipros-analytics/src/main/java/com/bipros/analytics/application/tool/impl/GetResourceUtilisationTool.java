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
 * Per-resource utilisation summary across active assignments. Aggregates planned vs
 * actual units and computes a utilisation ratio. Optional project_id scoping.
 */
@AnalyticsTool(name = "get_resource_utilisation",
        description = "Per-resource utilisation summary (planned vs actual units) for a project, optional date range.")
@Slf4j
public class GetResourceUtilisationTool implements AnalyticsToolHandler<GetResourceUtilisationTool.Req> {

    private final ObjectMapper objectMapper;
    private final ProjectAccessService projectAccessService;
    private final JdbcTemplate ch;

    public GetResourceUtilisationTool(ObjectMapper objectMapper,
                                      ProjectAccessService projectAccessService,
                                      @Qualifier("clickhouseReaderJdbcTemplate") JdbcTemplate ch) {
        this.objectMapper = objectMapper;
        this.projectAccessService = projectAccessService;
        this.ch = ch;
    }

    public record Req(UUID project_id, LocalDate from_date, LocalDate to_date, Integer limit) {}

    @Override public String name() { return "get_resource_utilisation"; }
    @Override public Class<Req> requestType() { return Req.class; }

    @Override
    public JsonNode inputSchema() {
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_id", Map.of("type", "string", "format", "uuid"),
                        "from_date", Map.of("type", "string", "format", "date",
                                "description", "Optional. Inclusive lower bound on assignment planned_start_date."),
                        "to_date", Map.of("type", "string", "format", "date",
                                "description", "Optional. Inclusive upper bound on assignment planned_finish_date."),
                        "limit", Map.of("type", "integer", "description", "Default 50, max 200.")
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

        int limit = clamp(req.limit != null ? req.limit : 50, 1, 200);

        StringBuilder sql = new StringBuilder(
                "SELECT ra.resource_id AS resource_id, r.code AS resource_code, r.name AS resource_name, "
                        + "r.resource_type AS resource_type, "
                        + "sum(coalesce(ra.planned_units, 0)) AS planned_units, "
                        + "sum(coalesce(ra.actual_units, 0)) AS actual_units, "
                        + "sum(coalesce(ra.remaining_units, 0)) AS remaining_units, "
                        + "if(sum(coalesce(ra.planned_units, 0)) = 0, NULL, "
                        + "sum(coalesce(ra.actual_units, 0)) / sum(coalesce(ra.planned_units, 0)) * 100) "
                        + "AS utilisation_pct "
                        + "FROM bipros_analytics.fact_resource_assignments ra FINAL "
                        + "LEFT JOIN bipros_analytics.dim_resource r FINAL ON ra.resource_id = r.id "
                        + "WHERE ra.project_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(req.project_id.toString());
        if (req.from_date != null) {
            sql.append(" AND ra.planned_start_date >= ?");
            args.add(java.sql.Date.valueOf(req.from_date));
        }
        if (req.to_date != null) {
            sql.append(" AND ra.planned_finish_date <= ?");
            args.add(java.sql.Date.valueOf(req.to_date));
        }
        sql.append(" GROUP BY ra.resource_id, r.code, r.name, r.resource_type ")
                .append(" ORDER BY actual_units DESC LIMIT ").append(limit);

        List<Map<String, Object>> rows = ch.queryForList(sql.toString(), args.toArray());
        List<String> cols = List.of("resource_id", "resource_code", "resource_name", "resource_type",
                "planned_units", "actual_units", "remaining_units", "utilisation_pct");
        String narrative = rows.isEmpty()
                ? "No resource assignments found."
                : rows.size() + " resource(s) returned.";
        return new ToolResult(narrative, cols, rows, null);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
