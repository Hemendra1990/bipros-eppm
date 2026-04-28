package com.bipros.analytics.application.tool.impl;

import com.bipros.analytics.application.tool.AnalyticsTool;
import com.bipros.analytics.application.tool.AnalyticsToolHandler;
import com.bipros.analytics.application.tool.AuthContext;
import com.bipros.analytics.application.tool.ToolResult;
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
 * Lists projects the caller can read. Filters by {@code AuthContext.accessibleProjectIds()}
 * (or no filter for ADMIN). Optional {@code status} filter; results capped at 200 rows.
 */
@AnalyticsTool(name = "list_projects",
        description = "List projects the user has read access to. Optional status filter.")
@Slf4j
public class ListProjectsTool implements AnalyticsToolHandler<ListProjectsTool.Req> {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate ch;

    public ListProjectsTool(ObjectMapper objectMapper,
                            @Qualifier("clickhouseReaderJdbcTemplate") JdbcTemplate ch) {
        this.objectMapper = objectMapper;
        this.ch = ch;
    }

    public record Req(String status, Integer limit) {}

    @Override public String name() { return "list_projects"; }
    @Override public Class<Req> requestType() { return Req.class; }

    @Override
    public JsonNode inputSchema() {
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of("type", "string",
                                "description", "Filter by project status (e.g. ACTIVE, COMPLETED). Optional."),
                        "limit", Map.of("type", "integer",
                                "description", "Max rows. Default 50, max 200.")
                ),
                "additionalProperties", false
        ));
    }

    @Override
    public ToolResult execute(Req req, AuthContext auth) {
        int limit = clamp(req != null && req.limit != null ? req.limit : 50, 1, 200);
        StringBuilder sql = new StringBuilder(
                "SELECT id, code, name, status, planned_start_date, planned_finish_date "
                        + "FROM bipros_analytics.dim_project FINAL WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (auth.accessibleProjectIds() != null) {
            if (auth.accessibleProjectIds().isEmpty()) {
                return ToolResult.empty("You don't have access to any projects.");
            }
            sql.append(" AND id IN (")
                    .append(String.join(",", Collections.nCopies(auth.accessibleProjectIds().size(), "?")))
                    .append(")");
            for (UUID id : auth.accessibleProjectIds()) args.add(id.toString());
        }
        if (req != null && req.status != null && !req.status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(req.status);
        }
        sql.append(" ORDER BY name LIMIT ").append(limit);

        List<Map<String, Object>> rows = ch.queryForList(sql.toString(), args.toArray());
        List<String> cols = List.of("id", "code", "name", "status",
                "planned_start_date", "planned_finish_date");
        String narrative = rows.isEmpty()
                ? "No projects matched the filters."
                : "Found " + rows.size() + " project(s).";
        return new ToolResult(narrative, cols, rows, null);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
