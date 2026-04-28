package com.bipros.analytics.application.sql;

import org.apache.calcite.sql.SqlNode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScopeRewriterTest {

    private final SqlSafetyValidator v = new SqlSafetyValidator();
    private final SqlScopeRewriter r = new SqlScopeRewriter();

    @Test
    void injectsProjectIdInPredicate() {
        UUID p1 = UUID.randomUUID();
        SqlNode parsed = v.parseAndValidate(
                "SELECT schedule_performance_index FROM bipros_analytics.fact_evm_snapshots");
        SqlNode rewritten = r.rewrite(parsed, Set.of(p1));
        String s = rewritten.toString().toLowerCase();
        assertTrue(s.contains("project_id"), "expected project_id predicate, got: " + s);
        assertTrue(s.contains(p1.toString().toLowerCase()), "expected uuid in predicate: " + s);
    }

    @Test
    void injectsIdForDimProject() {
        UUID p1 = UUID.randomUUID();
        SqlNode parsed = v.parseAndValidate(
                "SELECT name FROM bipros_analytics.dim_project");
        SqlNode rewritten = r.rewrite(parsed, Set.of(p1));
        String s = rewritten.toString().toLowerCase();
        assertTrue(s.contains("id"), "expected id predicate, got: " + s);
        assertTrue(s.contains(p1.toString().toLowerCase()));
    }

    @Test
    void joinWithFactPrefersProjectId() {
        UUID p1 = UUID.randomUUID();
        SqlNode parsed = v.parseAndValidate(
                "SELECT p.name, e.schedule_performance_index "
                        + "FROM bipros_analytics.dim_project p "
                        + "JOIN bipros_analytics.fact_evm_snapshots e ON p.id = e.project_id");
        SqlNode rewritten = r.rewrite(parsed, Set.of(p1));
        String s = rewritten.toString().toLowerCase();
        assertTrue(s.contains("project_id"));
    }

    @Test
    void adminPassthrough() {
        SqlNode parsed = v.parseAndValidate(
                "SELECT schedule_performance_index FROM bipros_analytics.fact_evm_snapshots");
        SqlNode rewritten = r.rewrite(parsed, null);
        assertEquals(parsed.toString(), rewritten.toString());
    }

    @Test
    void emptyAccessRejects() {
        SqlNode parsed = v.parseAndValidate(
                "SELECT schedule_performance_index FROM bipros_analytics.fact_evm_snapshots");
        assertThrows(SqlNotAllowedException.class, () -> r.rewrite(parsed, Set.of()));
    }

    @Test
    void noScopeBearingTableNoRewrite() {
        UUID p1 = UUID.randomUUID();
        SqlNode parsed = v.parseAndValidate(
                "SELECT name FROM bipros_analytics.dim_user");
        SqlNode rewritten = r.rewrite(parsed, Set.of(p1));
        String s = rewritten.toString().toLowerCase();
        // dim_user has no project scope; predicate must NOT be injected.
        assertTrue(!s.contains("project_id") && !s.contains(p1.toString().toLowerCase()),
                "expected no scope predicate on dim_user, got: " + s);
    }
}
