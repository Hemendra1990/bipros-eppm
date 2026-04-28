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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Project header (name/status/dates) plus the most recent EVM snapshot for the project.
 * Throws {@code AccessDeniedException} via {@link ProjectAccessService#requireRead(UUID)}
 * when the caller lacks read access — caught by the orchestrator and turned into a polite
 * refusal.
 */
@AnalyticsTool(name = "get_project_overview",
        description = "Project header plus latest EVM SPI/CPI for a project. Requires project_id.")
@Slf4j
public class GetProjectOverviewTool implements AnalyticsToolHandler<GetProjectOverviewTool.Req> {

    private final ObjectMapper objectMapper;
    private final ProjectAccessService projectAccessService;
    private final JdbcTemplate ch;

    public GetProjectOverviewTool(ObjectMapper objectMapper,
                                  ProjectAccessService projectAccessService,
                                  @Qualifier("clickhouseReaderJdbcTemplate") JdbcTemplate ch) {
        this.objectMapper = objectMapper;
        this.projectAccessService = projectAccessService;
        this.ch = ch;
    }

    public record Req(UUID project_id) {}

    @Override public String name() { return "get_project_overview"; }
    @Override public Class<Req> requestType() { return Req.class; }

    @Override
    public JsonNode inputSchema() {
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_id", Map.of("type", "string", "format", "uuid",
                                "description", "Project UUID")
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

        Map<String, Object> header;
        try {
            header = ch.queryForMap(
                    "SELECT id, code, name, status, planned_start_date, planned_finish_date "
                            + "FROM bipros_analytics.dim_project FINAL WHERE id = ?",
                    req.project_id.toString());
        } catch (EmptyResultDataAccessException e) {
            return ToolResult.empty("Project not found.");
        }

        List<Map<String, Object>> evm = ch.queryForList(
                "SELECT data_date, planned_value, earned_value, actual_cost, "
                        + "schedule_performance_index, cost_performance_index "
                        + "FROM bipros_analytics.fact_evm_snapshots FINAL "
                        + "WHERE project_id = ? "
                        + "ORDER BY data_date DESC LIMIT 1",
                req.project_id.toString());

        Map<String, Object> merged = new LinkedHashMap<>(header);
        if (!evm.isEmpty()) merged.putAll(evm.get(0));

        List<String> cols = new ArrayList<>(merged.keySet());
        String narrative = "Overview for project " + header.get("name") + ".";
        return new ToolResult(narrative, cols, List.of(merged), null);
    }
}
