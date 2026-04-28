package com.bipros.analytics.application.sql;

import java.util.List;
import java.util.Map;

/**
 * Allowlist + scope-column mapping for the {@code execute_sql} fallback.
 *
 * <p>Allowed table prefixes: {@code fact_*, dim_*, agg_*, vw_*}. Anything outside
 * {@code bipros_analytics} is rejected.
 *
 * <p>Scope-bearing tables (those that should have a {@code project_id IN (...)} or
 * {@code id IN (...)} predicate injected by {@link SqlScopeRewriter}) are listed in
 * {@link #SCOPE_BEARING_COLUMN}. {@code dim_project} uses its own {@code id} column
 * for scoping (since the table itself IS the project dimension); other tables use
 * {@code project_id}.
 *
 * <p>Tables NOT listed (e.g. {@code dim_resource}, {@code dim_user}) are public
 * dimensions — no project scope is injected, but row-level masking via JsonView still
 * applies on the response side.
 */
public final class SqlAllowlist {

    public static final String DB = "bipros_analytics";

    public static final List<String> TABLE_PREFIXES =
            List.of("fact_", "dim_", "agg_", "vw_");

    public static final Map<String, String> SCOPE_BEARING_COLUMN = Map.ofEntries(
            Map.entry("dim_project", "id"),
            Map.entry("dim_wbs", "project_id"),
            Map.entry("dim_activity", "project_id"),
            Map.entry("fact_evm_snapshots", "project_id"),
            Map.entry("fact_activity_progress", "project_id"),
            Map.entry("fact_dpr_lines", "project_id"),
            Map.entry("fact_activity_expenses", "project_id"),
            Map.entry("fact_resource_assignments", "project_id"),
            Map.entry("fact_baseline_variance", "project_id"),
            Map.entry("fact_risks", "project_id"),
            Map.entry("fact_contracts", "project_id"),
            Map.entry("fact_resource_daily_logs", "project_id"),
            Map.entry("fact_equipment_logs", "project_id"),
            Map.entry("fact_labour_returns", "project_id")
    );

    private SqlAllowlist() {}
}
