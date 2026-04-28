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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Activity-level cost variance from {@code fact_activity_expenses}: budgeted vs. actual,
 * with the absolute and percentage variance. Sorted by overrun magnitude descending.
 */
@AnalyticsTool(name = "get_cost_variance",
        description = "Per-activity cost variance (budgeted vs actual) for a project. Top N overruns.")
@Slf4j
public class GetCostVarianceTool implements AnalyticsToolHandler<GetCostVarianceTool.Req> {

    private final ObjectMapper objectMapper;
    private final ProjectAccessService projectAccessService;
    private final JdbcTemplate ch;

    public GetCostVarianceTool(ObjectMapper objectMapper,
                               ProjectAccessService projectAccessService,
                               @Qualifier("clickhouseReaderJdbcTemplate") JdbcTemplate ch) {
        this.objectMapper = objectMapper;
        this.projectAccessService = projectAccessService;
        this.ch = ch;
    }

    public record Req(UUID project_id, Boolean overrun_only, Integer limit) {}

    @Override public String name() { return "get_cost_variance"; }
    @Override public Class<Req> requestType() { return Req.class; }

    @Override
    public JsonNode inputSchema() {
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_id", Map.of("type", "string", "format", "uuid"),
                        "overrun_only", Map.of("type", "boolean",
                                "description", "If true, only rows where actual_cost > budgeted_cost."),
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
        boolean overrunOnly = Boolean.TRUE.equals(req.overrun_only);

        StringBuilder sql = new StringBuilder(
                "SELECT activity_id, name AS expense_name, expense_category, "
                        + "budgeted_cost, actual_cost, "
                        + "(coalesce(actual_cost, 0) - coalesce(budgeted_cost, 0)) AS cost_variance, "
                        + "if(coalesce(budgeted_cost, 0) = 0, NULL, "
                        + "(coalesce(actual_cost, 0) - coalesce(budgeted_cost, 0)) / budgeted_cost * 100) "
                        + "AS cost_variance_pct, "
                        + "percent_complete, currency "
                        + "FROM bipros_analytics.fact_activity_expenses FINAL "
                        + "WHERE project_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(req.project_id.toString());
        if (overrunOnly) sql.append(" AND coalesce(actual_cost, 0) > coalesce(budgeted_cost, 0)");
        sql.append(" ORDER BY (coalesce(actual_cost, 0) - coalesce(budgeted_cost, 0)) DESC ")
                .append("LIMIT ").append(limit);

        List<Map<String, Object>> rows = ch.queryForList(sql.toString(), args.toArray());
        List<String> cols = List.of("activity_id", "expense_name", "expense_category",
                "budgeted_cost", "actual_cost", "cost_variance", "cost_variance_pct",
                "percent_complete", "currency");
        String narrative = rows.isEmpty()
                ? "No expense rows found."
                : rows.size() + " expense row(s) returned.";
        return new ToolResult(narrative, cols, rows, null);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
