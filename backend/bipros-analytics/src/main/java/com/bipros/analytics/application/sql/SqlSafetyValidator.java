package com.bipros.analytics.application.sql;

import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOrderBy;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlWith;
import org.apache.calcite.sql.SqlWithItem;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Parses an SQL string with Calcite and rejects anything that isn't a safe SELECT
 * against the bipros_analytics allowlist. Returns the parsed {@link SqlNode} for
 * downstream rewriting by {@link SqlScopeRewriter}.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>Multi-statement input is rejected (any non-trailing semicolon).</li>
 *   <li>Top-level node must be one of {@code SELECT, ORDER_BY, WITH, UNION, INTERSECT,
 *       EXCEPT}. Anything else (DDL, INSERT, UPDATE, DELETE, EXPLAIN, …) is rejected.</li>
 *   <li>Every base-table reference must live in {@code bipros_analytics} and have a
 *       name starting with {@code fact_/dim_/agg_/vw_}.</li>
 * </ul>
 */
@Component
public class SqlSafetyValidator {

    private static final SqlParser.Config PARSER_CONFIG = SqlParser.config()
            .withLex(Lex.MYSQL)
            .withConformance(SqlConformanceEnum.LENIENT);

    public SqlNode parseAndValidate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlNotAllowedException("Empty SQL");
        }
        if (containsBareSemicolon(sql)) {
            throw new SqlNotAllowedException("Multi-statement SQL is not allowed");
        }

        SqlNode node;
        try {
            node = SqlParser.create(sql, PARSER_CONFIG).parseStmt();
        } catch (SqlParseException e) {
            throw new SqlNotAllowedException("SQL parse error: " + e.getMessage());
        }

        SqlKind k = node.getKind();
        if (k != SqlKind.SELECT && k != SqlKind.ORDER_BY && k != SqlKind.WITH
                && k != SqlKind.UNION && k != SqlKind.INTERSECT && k != SqlKind.EXCEPT) {
            throw new SqlNotAllowedException("Only SELECT statements are allowed (got " + k + ")");
        }

        Set<String> tables = new HashSet<>();
        collectBaseTables(node, tables);
        if (tables.isEmpty()) {
            throw new SqlNotAllowedException("No tables referenced");
        }
        for (String t : tables) {
            if (!isAllowedTable(t)) {
                throw new SqlNotAllowedException("Table not allowed: " + t);
            }
        }
        return node;
    }

    /**
     * Walks the AST and harvests every identifier that appears in a FROM/JOIN position.
     * Only base tables — column references and function names are skipped.
     */
    static void collectBaseTables(SqlNode node, Set<String> out) {
        if (node == null) return;

        if (node instanceof SqlOrderBy ob) {
            collectBaseTables(ob.query, out);
            return;
        }
        if (node instanceof SqlWith with) {
            for (SqlNode item : with.withList) {
                if (item instanceof SqlWithItem wi) {
                    collectBaseTables(wi.query, out);
                }
            }
            collectBaseTables(with.body, out);
            return;
        }
        if (node instanceof SqlSelect select) {
            collectFromTables(select.getFrom(), out);
            // Sub-queries inside WHERE are also walked.
            collectBaseTables(select.getWhere(), out);
            return;
        }
        if (node instanceof SqlJoin join) {
            collectFromTables(join.getLeft(), out);
            collectFromTables(join.getRight(), out);
            return;
        }
        if (node instanceof SqlBasicCall call) {
            // UNION / INTERSECT / EXCEPT have their operands as SqlSelect/SqlBasicCall.
            for (SqlNode op : call.getOperandList()) {
                collectBaseTables(op, out);
            }
            return;
        }
        if (node instanceof SqlCall call) {
            for (SqlNode op : call.getOperandList()) {
                collectBaseTables(op, out);
            }
        }
    }

    private static void collectFromTables(SqlNode from, Set<String> out) {
        if (from == null) return;

        if (from instanceof SqlIdentifier id) {
            out.add(String.join(".", id.names));
            return;
        }
        if (from instanceof SqlJoin join) {
            collectFromTables(join.getLeft(), out);
            collectFromTables(join.getRight(), out);
            return;
        }
        if (from instanceof SqlBasicCall call
                && call.getOperator() == SqlStdOperatorTable.AS) {
            // FROM table AS alias — the first operand is the table identifier or a sub-query.
            collectFromTables(call.operand(0), out);
            return;
        }
        // Sub-query in FROM: walk it as a regular node.
        collectBaseTables(from, out);
    }

    private static boolean containsBareSemicolon(String sql) {
        String trimmed = sql.strip();
        if (trimmed.endsWith(";")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed.contains(";");
    }

    private static boolean isAllowedTable(String fqn) {
        String[] parts = fqn.split("\\.");
        String db, table;
        if (parts.length == 2) { db = parts[0]; table = parts[1]; }
        else if (parts.length == 1) { db = SqlAllowlist.DB; table = parts[0]; }
        else return false;
        if (!SqlAllowlist.DB.equalsIgnoreCase(db)) return false;
        String lower = table.toLowerCase();
        for (String prefix : SqlAllowlist.TABLE_PREFIXES) {
            if (lower.startsWith(prefix)) return true;
        }
        return false;
    }
}
