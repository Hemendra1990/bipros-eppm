package com.bipros.analytics.application.sql;

import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlWith;
import org.apache.calcite.sql.SqlWithItem;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * AND-attaches a project-scope predicate to every {@link SqlSelect} that references a
 * scope-bearing table in its FROM. Predicate column comes from
 * {@link SqlAllowlist#SCOPE_BEARING_COLUMN} (most tables use {@code project_id};
 * {@code dim_project} uses its own {@code id}).
 *
 * <p>ADMIN ({@code allowedProjectIds == null}) is a no-op pass-through. An empty set
 * is rejected — the caller has zero accessible projects so the request can't return
 * anything meaningful, and emitting {@code IN ()} is a SQL syntax error.
 *
 * <p>Sub-queries inside WHERE/FROM each get their own scope predicate. This is
 * conservative; over-filtering is fine because the read-only ClickHouse user is the
 * second backstop.
 */
@Component
public class SqlScopeRewriter {

    public SqlNode rewrite(SqlNode root, Set<UUID> allowedProjectIds) {
        if (allowedProjectIds == null) return root;
        if (allowedProjectIds.isEmpty()) {
            throw new SqlNotAllowedException("User has no accessible projects");
        }
        rewriteRecursive(root, allowedProjectIds);
        return root;
    }

    private static void rewriteRecursive(SqlNode node, Set<UUID> allowed) {
        if (node == null) return;

        if (node instanceof SqlOrderBy ob) {
            rewriteRecursive(ob.query, allowed);
            return;
        }
        if (node instanceof SqlWith with) {
            for (SqlNode item : with.withList) {
                if (item instanceof SqlWithItem wi) {
                    rewriteRecursive(wi.query, allowed);
                }
            }
            rewriteRecursive(with.body, allowed);
            return;
        }
        if (node instanceof SqlSelect select) {
            rewriteRecursive(select.getFrom(), allowed);
            rewriteRecursive(select.getWhere(), allowed);

            String scopeColumn = pickScopeColumn(select.getFrom());
            if (scopeColumn != null) {
                SqlNode predicate = buildPredicate(scopeColumn, allowed);
                SqlNode newWhere = select.getWhere() == null
                        ? predicate
                        : SqlStdOperatorTable.AND.createCall(
                                SqlParserPos.ZERO, select.getWhere(), predicate);
                select.setWhere(newWhere);
            }
            return;
        }
        if (node instanceof SqlJoin join) {
            rewriteRecursive(join.getLeft(), allowed);
            rewriteRecursive(join.getRight(), allowed);
            return;
        }
        if (node instanceof SqlBasicCall call) {
            for (SqlNode op : call.getOperandList()) {
                rewriteRecursive(op, allowed);
            }
        }
    }

    /**
     * Picks the scope column for a SELECT's FROM clause. Returns null if the FROM
     * touches no scope-bearing table. If the FROM has multiple scope-bearing tables
     * (a JOIN), {@code project_id} wins over {@code id} so the predicate matches
     * the most common case (joins involving fact tables).
     */
    private static String pickScopeColumn(SqlNode from) {
        List<String> tables = new ArrayList<>();
        collectFromTableNames(from, tables);
        boolean hasProjectIdTable = false;
        boolean hasDimProject = false;
        for (String t : tables) {
            String last = t.substring(t.lastIndexOf('.') + 1).toLowerCase();
            String col = SqlAllowlist.SCOPE_BEARING_COLUMN.get(last);
            if (col == null) continue;
            if ("project_id".equals(col)) hasProjectIdTable = true;
            else if ("id".equals(col) && "dim_project".equals(last)) hasDimProject = true;
        }
        if (hasProjectIdTable) return "project_id";
        if (hasDimProject) return "id";
        return null;
    }

    private static void collectFromTableNames(SqlNode from, List<String> out) {
        if (from == null) return;
        if (from instanceof SqlIdentifier id) {
            out.add(String.join(".", id.names));
            return;
        }
        if (from instanceof SqlJoin join) {
            collectFromTableNames(join.getLeft(), out);
            collectFromTableNames(join.getRight(), out);
            return;
        }
        if (from instanceof SqlBasicCall call
                && call.getOperator() == SqlStdOperatorTable.AS) {
            collectFromTableNames(call.operand(0), out);
        }
    }

    private static SqlNode buildPredicate(String column, Set<UUID> allowed) {
        SqlIdentifier col = new SqlIdentifier(List.of(column), SqlParserPos.ZERO);
        List<SqlNode> literals = new ArrayList<>(allowed.size());
        for (UUID id : allowed) {
            literals.add(SqlLiteral.createCharString(id.toString(), SqlParserPos.ZERO));
        }
        SqlNodeList list = new SqlNodeList(literals, SqlParserPos.ZERO);
        return SqlStdOperatorTable.IN.createCall(SqlParserPos.ZERO, col, list);
    }
}
