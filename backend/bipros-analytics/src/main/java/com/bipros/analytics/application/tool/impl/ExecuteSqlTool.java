package com.bipros.analytics.application.tool.impl;

import com.bipros.analytics.application.sql.SqlSafetyValidator;
import com.bipros.analytics.application.sql.SqlScopeRewriter;
import com.bipros.analytics.application.tool.AnalyticsTool;
import com.bipros.analytics.application.tool.AnalyticsToolHandler;
import com.bipros.analytics.application.tool.AuthContext;
import com.bipros.analytics.application.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.dialect.AnsiSqlDialect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * Read-only ad-hoc SQL fallback. Pipeline:
 * <ol>
 *   <li>{@link SqlSafetyValidator} parses and rejects DDL/DML/multi-statement/non-allowlisted.</li>
 *   <li>{@link SqlScopeRewriter} injects {@code project_id IN (...)} (or {@code id IN (...)}
 *       for {@code dim_project}) using {@link AuthContext#accessibleProjectIds()}.</li>
 *   <li>{@link JdbcTemplate} runs the rewritten SQL against the {@code bipros_reader}
 *       ClickHouse user (no DDL/DML grants — even if a parser bypass slips through, no data
 *       can be modified).</li>
 * </ol>
 * Per-query caps via {@code setMaxRows} + {@code setQueryTimeout}.
 */
@AnalyticsTool(name = "execute_sql",
        description = "Read-only ad-hoc SQL against bipros_analytics fact_*/dim_*/agg_*/vw_* tables. "
                + "SELECT only. project_id scoping is enforced automatically.")
@Slf4j
public class ExecuteSqlTool implements AnalyticsToolHandler<ExecuteSqlTool.Req> {

    private final ObjectMapper objectMapper;
    private final SqlSafetyValidator validator;
    private final SqlScopeRewriter rewriter;
    private final JdbcTemplate ch;

    @Value("${bipros.analytics.sql.max-rows:10000}")
    private int maxRows;
    @Value("${bipros.analytics.sql.max-execution-time:10}")
    private int maxExecSeconds;

    public ExecuteSqlTool(ObjectMapper objectMapper,
                          SqlSafetyValidator validator,
                          SqlScopeRewriter rewriter,
                          @Qualifier("clickhouseReaderJdbcTemplate") JdbcTemplate ch) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.rewriter = rewriter;
        this.ch = ch;
    }

    public record Req(String sql) {}

    @Override public String name() { return "execute_sql"; }
    @Override public Class<Req> requestType() { return Req.class; }

    @Override
    public JsonNode inputSchema() {
        return objectMapper.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                        "sql", Map.of("type", "string",
                                "description", "ClickHouse SELECT against bipros_analytics fact_*/dim_*/"
                                        + "agg_*/vw_* tables. Single-statement, SELECT only.")
                ),
                "required", List.of("sql"),
                "additionalProperties", false
        ));
    }

    @Override
    public ToolResult execute(Req req, AuthContext auth) {
        if (req == null || req.sql == null) {
            throw new IllegalArgumentException("sql is required");
        }

        SqlNode parsed = validator.parseAndValidate(req.sql);
        SqlNode scoped = rewriter.rewrite(parsed, auth.accessibleProjectIds());
        String finalSql = scoped.toSqlString(AnsiSqlDialect.DEFAULT).getSql();

        ch.setMaxRows(maxRows);
        ch.setQueryTimeout(maxExecSeconds);

        List<Map<String, Object>> rows = ch.queryForList(finalSql);
        List<String> cols = rows.isEmpty() ? List.of() : List.copyOf(rows.get(0).keySet());
        String narrative = "Returned " + rows.size() + " row(s).";
        return new ToolResult(narrative, cols, rows, finalSql);
    }
}
