package com.bipros.analytics.query;

import com.bipros.common.exception.BusinessRuleException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guards AI-generated SQL. Enforces:
 * <ul>
 *   <li>SELECT only (no DML/DDL).</li>
 *   <li>Tables drawn from the analytics warehouse allow-list.</li>
 *   <li>{@code project_id} predicate present and every UUID literal compared to
 *       {@code project_id} is in {@code scopedProjectIds}. This stops the LLM
 *       from running {@code WHERE project_id = '&lt;any uuid it picked&gt;'} and
 *       reading projects the user can't access.</li>
 *   <li>{@code LIMIT} ≤ {@value #MAX_LIMIT}.</li>
 * </ul>
 *
 * <p>When the caller passes a single-element {@code scopedProjectIds}, the
 * effect is "this SQL may only touch that one project" — the natural
 * enforcement of {@code ctx.projectId()} when a project is in scope.
 */
@Component
public class SqlGuard {

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "dim_project", "dim_wbs", "dim_activity", "dim_resource", "dim_cost_account", "dim_calendar",
            "dim_risk", "dim_permit", "dim_permit_type", "dim_labour_designation",
            "fact_activity_progress_daily", "fact_resource_usage_daily", "fact_cost_daily",
            "fact_evm_daily", "fact_dpr_logs",
            "fact_dpr_manpower_daily", "fact_dpr_equipment_daily", "fact_dpr_material_daily",
            "fact_risk_snapshot_daily", "fact_permit_lifecycle", "fact_labour_daily",
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

        // Validate every UUID literal compared to a `project_id` column is in scope.
        // This blocks the LLM from selecting a project the user cannot access — the
        // core defense against project-scope confusion in the agent loop.
        Set<String> scope = new HashSet<>(scopedProjectIds);
        ProjectIdScopeValidator validator = new ProjectIdScopeValidator(scope);
        if (ps.getWhere() != null) ps.getWhere().accept(validator);
        if (ps.getJoins() != null) {
            for (Join j : ps.getJoins()) {
                if (j.getOnExpressions() != null) {
                    for (Expression on : j.getOnExpressions()) {
                        on.accept(validator);
                    }
                }
            }
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

    /**
     * Walks the WHERE / JOIN-ON expression tree and validates every literal
     * compared to a {@code project_id} column is in the authorized scope.
     * Comparisons of column-to-column (e.g. {@code f.project_id = a.project_id}
     * in a JOIN) are ignored — only string literals trigger validation.
     */
    private static final class ProjectIdScopeValidator extends ExpressionVisitorAdapter<Void> {
        private final Set<String> scope;

        ProjectIdScopeValidator(Set<String> scope) {
            this.scope = scope;
        }

        @Override
        public <S> Void visit(EqualsTo expr, S context) {
            checkEquality(expr.getLeftExpression(), expr.getRightExpression());
            checkEquality(expr.getRightExpression(), expr.getLeftExpression());
            return super.visit(expr, context);
        }

        @Override
        public <S> Void visit(InExpression expr, S context) {
            Expression left = expr.getLeftExpression();
            if (!isProjectIdColumn(left)) {
                return super.visit(expr, context);
            }
            Expression right = expr.getRightExpression();
            if (right instanceof ExpressionList<?>) {
                for (Expression e : ((ExpressionList<?>) right).getExpressions()) {
                    if (e instanceof StringValue) {
                        validate(((StringValue) e).getValue());
                    }
                }
            }
            return super.visit(expr, context);
        }

        private void checkEquality(Expression columnSide, Expression valueSide) {
            if (!isProjectIdColumn(columnSide)) return;
            if (!(valueSide instanceof StringValue)) return;
            validate(((StringValue) valueSide).getValue());
        }

        private boolean isProjectIdColumn(Expression e) {
            if (!(e instanceof Column)) return false;
            String name = ((Column) e).getColumnName();
            return name != null && name.equalsIgnoreCase("project_id");
        }

        private void validate(String uuid) {
            if (uuid == null || uuid.isBlank()) return;
            if (!scope.contains(uuid)) {
                throw new BusinessRuleException(
                        "SQL_PROJECT_OUT_OF_SCOPE",
                        "Query references project_id '" + uuid + "' which is not in your "
                                + "authorized scope. Use a project from your accessible list. "
                                + "Authorized scope (truncated): "
                                + previewScope(scope));
            }
        }

        private static String previewScope(Set<String> scope) {
            if (scope.isEmpty()) return "(none)";
            if (scope.size() <= 3) return String.join(", ", scope);
            return scope.stream().limit(3).reduce((a, b) -> a + ", " + b).orElse("")
                    + ", … (" + (scope.size() - 3) + " more)";
        }
    }
}
