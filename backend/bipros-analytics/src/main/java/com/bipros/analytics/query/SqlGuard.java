package com.bipros.analytics.query;

import com.bipros.common.exception.BusinessRuleException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Guards AI-generated SQL:只允许 SELECT, 检查表白名单, 强制 project_id IN 过滤, 限制 LIMIT.
 */
@Component
public class SqlGuard {

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "dim_project", "dim_wbs", "dim_activity", "dim_resource", "dim_cost_account", "dim_calendar",
            "dim_risk", "dim_permit", "dim_permit_type", "dim_labour_designation",
            "fact_activity_progress_daily", "fact_resource_usage_daily", "fact_cost_daily",
            "fact_evm_daily", "fact_dpr_logs", "fact_risk_snapshot_daily",
            "fact_permit_lifecycle", "fact_labour_daily",
            "mv_project_kpi_daily", "mv_portfolio_scurve_weekly", "mv_activity_weekly"
    );

    private static final int MAX_LIMIT = 5000;

    public void validate(String sql, List<String> scopedProjectIds) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessRuleException("SQL_EMPTY", "SQL is empty");
        }

        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            throw new BusinessRuleException("SQL_PARSE_ERROR", "Failed to parse SQL: " + e.getMessage());
        }

        if (!(stmt instanceof Select)) {
            throw new BusinessRuleException("SQL_NOT_SELECT", "Only SELECT queries are allowed");
        }

        Select select = (Select) stmt;
        PlainSelect ps = (PlainSelect) select.getSelectBody();

        // Check tables
        TablesNamesFinder finder = new TablesNamesFinder();
        List<String> tables = finder.getTableList(stmt);
        for (String t : tables) {
            String bare = t.replace("bipros_analytics.", "");
            if (!ALLOWED_TABLES.contains(bare)) {
                throw new BusinessRuleException("SQL_TABLE_NOT_ALLOWED",
                        "Table not allowed: " + bare);
            }
        }

        // Check project_id predicate presence (simple heuristic)
        String lower = sql.toLowerCase();
        if (!lower.contains("project_id")) {
            throw new BusinessRuleException("SQL_MISSING_PROJECT_FILTER",
                    "Query must include project_id filter");
        }

        // Check limit
        if (ps.getLimit() != null && ps.getLimit().getRowCount() != null) {
            String limitStr = ps.getLimit().getRowCount().toString();
            try {
                int limit = Integer.parseInt(limitStr);
                if (limit > MAX_LIMIT) {
                    throw new BusinessRuleException("SQL_LIMIT_TOO_HIGH",
                            "LIMIT exceeds " + MAX_LIMIT);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
