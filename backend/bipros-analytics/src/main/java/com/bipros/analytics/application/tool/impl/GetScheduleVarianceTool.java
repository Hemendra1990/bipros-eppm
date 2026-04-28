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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Activity-level schedule variance vs. baseline. Sorted by absolute finish-variance
 * descending so the worst slippages surface first. Capped at 100 rows.
 */
@AnalyticsTool(name = "get_schedule_variance",
        description = "Per-activity schedule variance vs. baseline (start/finish variance days). Top N most slipped.")
@Slf4j
public class GetScheduleVarianceTool implements AnalyticsToolHandler<GetScheduleVarianceTool.Req> {

    private final ObjectMapper objectMapper;
    private final ProjectAccessService projectAccessService;
    private final JdbcTemplate ch;

    public GetScheduleVarianceTool(ObjectMapper objectMapper,
                                   ProjectAccessService projectAccessService,
                                   @Qualifier("clickhouseReaderJdbcTemplate") JdbcTemplate ch) {
        this.objectMapper = objectMapper;
        this.projectAccessService = projectAccessService;
        this.ch = ch;
    }

    public record Req(UUID project_id, Boolean slipped_only, Integer limit) {}

    @Override public String name() { return "get_schedule_variance"; }
    @Override public Class<Req> requestType() { return Req.class; }

    @Override
    public JsonNode inputSchema() {
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_id", Map.of("type", "string", "format", "uuid"),
                        "slipped_only", Map.of("type", "boolean",
                                "description", "If true, only activities with positive finish_variance_days."),
                        "limit", Map.of("type", "integer",
                                "description", "Default 50, max 200.")
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
        boolean slippedOnly = Boolean.TRUE.equals(req.slipped_only);

        StringBuilder sql = new StringBuilder(
                "SELECT bv.activity_id AS activity_id, a.code AS activity_code, a.name AS activity_name, "
                        + "bv.baseline_early_start, bv.baseline_early_finish, "
                        + "bv.current_planned_start, bv.current_planned_finish, "
                        + "bv.current_actual_start, bv.current_actual_finish, "
                        + "bv.start_variance_days, bv.finish_variance_days, bv.duration_variance, "
                        + "bv.percent_complete_variance "
                        + "FROM bipros_analytics.fact_baseline_variance bv FINAL "
                        + "LEFT JOIN bipros_analytics.dim_activity a FINAL ON bv.activity_id = a.id "
                        + "WHERE bv.project_id = ?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(req.project_id.toString());
        if (slippedOnly) sql.append(" AND bv.finish_variance_days > 0");
        sql.append(" ORDER BY abs(coalesce(bv.finish_variance_days, 0)) DESC LIMIT ").append(limit);

        List<Map<String, Object>> rows = ch.queryForList(sql.toString(), args.toArray());
        List<String> cols = List.of("activity_id", "activity_code", "activity_name",
                "baseline_early_start", "baseline_early_finish",
                "current_planned_start", "current_planned_finish",
                "current_actual_start", "current_actual_finish",
                "start_variance_days", "finish_variance_days", "duration_variance",
                "percent_complete_variance");
        String narrative = rows.isEmpty()
                ? "No baseline variance records found."
                : rows.size() + " activity variance row(s) returned.";
        return new ToolResult(narrative, cols, rows, null);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
