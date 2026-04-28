package com.bipros.analytics.application.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlSafetyValidatorTest {

    private final SqlSafetyValidator v = new SqlSafetyValidator();

    @Test
    void acceptsBareSelect() {
        assertDoesNotThrow(() -> v.parseAndValidate(
                "SELECT project_id FROM bipros_analytics.fact_evm_snapshots "
                        + "WHERE project_id = '00000000-0000-0000-0000-000000000000'"));
    }

    @Test
    void acceptsJoinAcrossAllowlist() {
        assertDoesNotThrow(() -> v.parseAndValidate(
                "SELECT p.name, e.schedule_performance_index "
                        + "FROM bipros_analytics.dim_project p "
                        + "JOIN bipros_analytics.fact_evm_snapshots e ON p.id = e.project_id"));
    }

    @Test
    void rejectsDrop() {
        assertThrows(SqlNotAllowedException.class,
                () -> v.parseAndValidate("DROP TABLE bipros_analytics.fact_evm_snapshots"));
    }

    @Test
    void rejectsInsert() {
        assertThrows(SqlNotAllowedException.class,
                () -> v.parseAndValidate("INSERT INTO bipros_analytics.fact_risks VALUES (1)"));
    }

    @Test
    void rejectsUpdate() {
        assertThrows(SqlNotAllowedException.class,
                () -> v.parseAndValidate("UPDATE bipros_analytics.dim_project SET status='X'"));
    }

    @Test
    void rejectsForeignSchema() {
        assertThrows(SqlNotAllowedException.class,
                () -> v.parseAndValidate("SELECT * FROM system.tables"));
    }

    @Test
    void rejectsNonAllowlistedTable() {
        assertThrows(SqlNotAllowedException.class,
                () -> v.parseAndValidate("SELECT * FROM bipros_analytics.user_secrets"));
    }

    @Test
    void rejectsMultiStatement() {
        assertThrows(SqlNotAllowedException.class,
                () -> v.parseAndValidate("SELECT 1; SELECT 2"));
    }
}
