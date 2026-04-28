package com.bipros.analytics.application.service;

import com.bipros.analytics.application.tool.ToolDescriptor;
import com.bipros.analytics.application.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the system prompt for the LLM. Two halves:
 * <ul>
 *   <li>{@link #buildStaticPrefix()} — role description, available tools, ClickHouse schema
 *       digest. Stable across requests; suitable for a prompt-cache breakpoint with
 *       providers that support caching (e.g. Anthropic).</li>
 *   <li>{@link #buildDynamicSuffix(int, boolean)} — current date + accessible-project
 *       count. Changes per-request, so it goes outside the cache breakpoint.</li>
 * </ul>
 *
 * The schema digest is built once on first call by querying ClickHouse's
 * {@code system.tables} / {@code system.columns} and cached in-process for the
 * remainder of the JVM lifetime.
 */
@Service
@Slf4j
public class SystemPromptBuilder {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate ch;

    private volatile String cachedSchemaDigest;

    public SystemPromptBuilder(ToolRegistry toolRegistry,
                               ObjectMapper objectMapper,
                               @Qualifier("clickhouseReaderJdbcTemplate") JdbcTemplate ch) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.ch = ch;
    }

    public String buildStaticPrefix() {
        return """
                You are the analytics assistant for bipros-eppm, an enterprise project portfolio
                management system. You answer questions by calling tools that query a ClickHouse
                analytical store.

                Rules:
                1. ALWAYS use a tool to answer questions about projects, schedules, costs, risks,
                   or resources — do not invent data.
                2. Prefer the curated tools (list_projects, get_project_overview, get_evm_snapshot,
                   get_schedule_variance, get_cost_variance, get_top_risks,
                   get_resource_utilisation) over execute_sql. Only fall back to execute_sql when
                   no curated tool fits.
                3. If a tool returns an authorization error, politely explain that the user does
                   not have access to the requested data and suggest contacting their PMO.
                4. Keep narratives short — 1 to 3 sentences. The result table speaks for itself.
                5. UUIDs in WHERE clauses must be quoted strings.

                === Output format ===
                On every turn, respond with EXACTLY ONE compact JSON object — no markdown
                fencing, no commentary outside the JSON. Two shapes are valid:
                  • To call a tool:    {"tool": "<name>", "arguments": { ... }}
                  • To finish answering: {"answer": "<narrative>"}
                If a previous tool call returned an error or "ACCESS_DENIED", do not retry
                the same tool with the same arguments — finish with an "answer" that politely
                explains the situation.

                === Available tools ===
                """
                + renderTools()
                + """

                === ClickHouse schema digest ===
                All tables live in the bipros_analytics database. Most tables are project-scoped
                via a project_id column (dim_project uses its own id column for scoping).

                """
                + getSchemaDigest();
    }

    public String buildDynamicSuffix(int accessibleProjectCount, boolean isAdmin) {
        return "\n=== Current request context ===\n"
                + "- Today's date: " + LocalDate.now() + "\n"
                + "- Accessible projects: "
                + (isAdmin ? "all (admin)" : Integer.toString(accessibleProjectCount))
                + "\n";
    }

    private String renderTools() {
        return toolRegistry.descriptors().stream()
                .map(this::renderTool)
                .collect(Collectors.joining("\n"));
    }

    private String renderTool(ToolDescriptor d) {
        try {
            return "- " + d.name() + ": " + d.description() + "\n  schema: "
                    + objectMapper.writeValueAsString(d.inputSchema());
        } catch (Exception e) {
            return "- " + d.name() + ": " + d.description();
        }
    }

    private synchronized String getSchemaDigest() {
        if (cachedSchemaDigest != null) return cachedSchemaDigest;
        try {
            StringBuilder sb = new StringBuilder();
            List<Map<String, Object>> tables = ch.queryForList(
                    "SELECT name FROM system.tables WHERE database='bipros_analytics' "
                            + "AND (name LIKE 'fact_%' OR name LIKE 'dim_%' OR name LIKE 'agg_%' "
                            + "OR name LIKE 'vw_%') ORDER BY name");
            for (Map<String, Object> t : tables) {
                String name = String.valueOf(t.get("name"));
                sb.append(name).append(": ");
                List<Map<String, Object>> cols = ch.queryForList(
                        "SELECT name, type FROM system.columns "
                                + "WHERE database='bipros_analytics' AND table=? ORDER BY position",
                        name);
                sb.append(cols.stream()
                        .map(c -> c.get("name") + " " + c.get("type"))
                        .collect(Collectors.joining(", ")));
                sb.append("\n");
            }
            cachedSchemaDigest = sb.toString();
        } catch (Exception e) {
            log.warn("Failed to build ClickHouse schema digest; falling back to empty", e);
            cachedSchemaDigest = "(schema digest unavailable)\n";
        }
        return cachedSchemaDigest;
    }
}
