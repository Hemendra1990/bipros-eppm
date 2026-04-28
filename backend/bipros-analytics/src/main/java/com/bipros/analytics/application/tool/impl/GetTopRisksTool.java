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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Top-N risks by score. With {@code project_id} present, scoped to that project (after
 * {@link ProjectAccessService#requireRead(UUID)}). Without, runs across the caller's
 * accessible portfolio (handy for cross-project executive views).
 */
@AnalyticsTool(name = "get_top_risks",
        description = "Top-N risks by risk_score. project_id optional; without it, runs across the user's accessible projects.")
@Slf4j
public class GetTopRisksTool implements AnalyticsToolHandler<GetTopRisksTool.Req> {

    private final ObjectMapper objectMapper;
    private final ProjectAccessService projectAccessService;
    private final JdbcTemplate ch;

    public GetTopRisksTool(ObjectMapper objectMapper,
                           ProjectAccessService projectAccessService,
                           @Qualifier("clickhouseReaderJdbcTemplate") JdbcTemplate ch) {
        this.objectMapper = objectMapper;
        this.projectAccessService = projectAccessService;
        this.ch = ch;
    }

    public record Req(UUID project_id, String status, String rag, Integer limit) {}

    @Override public String name() { return "get_top_risks"; }
    @Override public Class<Req> requestType() { return Req.class; }

    @Override
    public JsonNode inputSchema() {
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_id", Map.of("type", "string", "format", "uuid",
                                "description", "Optional. If omitted, search across all accessible projects."),
                        "status", Map.of("type", "string",
                                "description", "Optional. Filter by status (e.g. OPEN, CLOSED, MITIGATING)."),
                        "rag", Map.of("type", "string",
                                "description", "Optional. Filter by RAG band (RED, AMBER, GREEN)."),
                        "limit", Map.of("type", "integer",
                                "description", "Default 10, max 100.")
                ),
                "additionalProperties", false
        ));
    }

    @Override
    public ToolResult execute(Req req, AuthContext auth) {
        int limit = clamp(req != null && req.limit != null ? req.limit : 10, 1, 100);

        StringBuilder sql = new StringBuilder(
                "SELECT id, project_id, code, title, status, risk_type, probability, impact, "
                        + "risk_score, rag, trend, due_date, cost_impact, schedule_impact_days "
                        + "FROM bipros_analytics.fact_risks FINAL WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (req != null && req.project_id != null) {
            projectAccessService.requireRead(req.project_id);
            sql.append(" AND project_id = ?");
            args.add(req.project_id.toString());
        } else if (auth.accessibleProjectIds() != null) {
            if (auth.accessibleProjectIds().isEmpty()) {
                return ToolResult.empty("You don't have access to any projects.");
            }
            sql.append(" AND project_id IN (")
                    .append(String.join(",", Collections.nCopies(auth.accessibleProjectIds().size(), "?")))
                    .append(")");
            for (UUID id : auth.accessibleProjectIds()) args.add(id.toString());
        }

        if (req != null && req.status != null && !req.status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(req.status);
        }
        if (req != null && req.rag != null && !req.rag.isBlank()) {
            sql.append(" AND rag = ?");
            args.add(req.rag);
        }

        sql.append(" ORDER BY coalesce(risk_score, 0) DESC LIMIT ").append(limit);

        List<Map<String, Object>> rows = ch.queryForList(sql.toString(), args.toArray());
        List<String> cols = List.of("id", "project_id", "code", "title", "status", "risk_type",
                "probability", "impact", "risk_score", "rag", "trend", "due_date",
                "cost_impact", "schedule_impact_days");
        String narrative = rows.isEmpty()
                ? "No risks matched the filters."
                : "Top " + rows.size() + " risk(s) returned.";
        return new ToolResult(narrative, cols, rows, null);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
