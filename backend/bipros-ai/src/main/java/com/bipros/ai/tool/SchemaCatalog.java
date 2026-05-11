package com.bipros.ai.tool;

import org.springframework.stereotype.Component;

/**
 * Central source of truth for the ClickHouse analytics schema description shown
 * to the agent. Both {@link QueryClickHouseTool#description()} and
 * {@link DescribeSchemaTool} read from here so the loop's view of the schema
 * never drifts.
 *
 * <p>If a developer reports that the AI keeps writing SQL the warehouse rejects
 * ("Unknown expression identifier 'uom'", "r.id type String, f.resource_id type
 * UUID", etc.), the local ClickHouse instance is stale relative to
 * {@code docker/clickhouse-init.sql}. The fix is one drop-and-replay — see
 * {@code docs/ai/local-clickhouse-reinit.md}.
 */
@Component
public class SchemaCatalog {

    private static final String FULL = """
            ClickHouse analytics warehouse `bipros_analytics`. SELECT only.
            Every query MUST include a `project_id` filter — `= '<uuid>'` for a single
            project or `IN ('<uuid1>','<uuid2>',...)` for cross-project. Quote UUIDs.

            PK column convention (READ THIS): every dim/fact has an explicit
            `<entity>_id` column — `dim_resource.resource_id`,
            `dim_activity.activity_id`, `dim_wbs.wbs_id`, `fact_dpr_logs.dpr_id`,
            etc. There is NO plain `id` column on any analytics table. When
            aliasing (e.g. `FROM bipros_analytics.dim_resource r`), still write
            `r.resource_id`, never `r.id`. The first column of each table listed
            below IS that PK. Don't carry `id` over from the OLTP catalog.

            Dimensions:
            - dim_project(project_id, code, name, status, portfolio_id, org_id, start_date, finish_date, currency, obs_node_id, updated_at)
            - dim_wbs(wbs_id, project_id, parent_wbs_id, code, name, level, weight, path)
            - dim_activity(activity_id, project_id, wbs_id, code, name, activity_type, uom, bq_quantity, planned_start, planned_finish, chainage_from_m, chainage_to_m, is_critical, responsible_resource_id, responsible_resource_name)
              -- responsible_resource_id = supervisor (Labor Resource) managing the activity. Soft FK to dim_resource.resource_id.
            - dim_resource(resource_id, project_id, resource_type, code, name, uom, unit_rate, is_subcontractor)
              -- CRITICAL: this table is ORG-WIDE, not project-scoped. The project_id column is
              -- always NULL because resources are defined globally and reused across projects.
              -- NEVER write `WHERE project_id = '<uuid>'` against dim_resource — it returns zero
              -- rows. To answer "which resources are on project X", join the project-scoped facts:
              --   SELECT DISTINCT r.* FROM bipros_analytics.fact_resource_usage_daily f
              --   LEFT JOIN bipros_analytics.dim_resource r ON r.resource_id = f.resource_id
              --   WHERE f.project_id = '<uuid>'
              -- BUT FOR THIS KIND OF QUESTION you should call the live JPA tool
              -- find_resource_deployment, NOT query_clickhouse — it queries the OLTP
              -- resource_assignments table directly and is override-aware.
              -- unit_rate is the resource's BASE rate (rate-master snapshot at last ETL sync) and
              -- does NOT reflect ProjectResource.rateOverride. Per-project rate questions →
              -- live tools (find_resource_deployment / get_resource_profile / list_activity_resources)
              -- which emit effective_rate, unit_basis, rate_source, override_applied.
            - dim_cost_account(cost_account_id, project_id, code, name, parent_id, category)
            - dim_calendar(date, year, quarter, month, week, iso_week, day_of_week, is_business_day, fiscal_period)
            - dim_risk(risk_id, project_id, code, title, risk_type, category_id, category_name, owner_id, owner_name, status, rag, trend, response_type, identified_date, identified_by_id, closed_date)
            - dim_permit_type(permit_type_template_id, code, name, color_hex, icon_key, max_duration_hours, requires_gas_test, requires_isolation, jsa_required, blasting_required, diving_required, default_risk_level, night_work_policy)
            - dim_permit(permit_id, project_id, permit_code, permit_type_template_id, parent_permit_id, status, risk_level, shift, contractor_org_id, location_zone, chainage_marker, supervisor_name, start_at, end_at, valid_from, valid_to, declaration_accepted_at, closed_at, closed_by, revoked_at, revoked_by, expired_at, suspended_at, total_approvals_required, approvals_completed)
            - dim_labour_designation(designation_id, code, designation, category, trade, grade, nationality, experience_years_min, default_daily_rate, skills, certifications, status)

            Facts (date-partitioned, ReplacingMergeTree):
            - fact_activity_progress_daily(project_id, activity_id, date, pct_complete_physical, pct_complete_duration, qty_executed, cumulative_qty, chainage_from_m, chainage_to_m, source, event_ts)
            - fact_resource_usage_daily(project_id, activity_id, resource_id, resource_type, date, hours_worked, days_worked, qty_executed, productivity_actual, productivity_norm, cost, event_ts)
              -- NOTE: cost is the value computed at ETL time using the resource's base rate snapshot
              -- (dim_resource.unit_rate). It does NOT carry unit_basis or a rate_override_applied
              -- flag, so SQL cannot distinguish rows whose cost reflected a per-project pool override
              -- from rows that used the base rate. For cost-precise per-resource questions on a single
              -- project, prefer list_activity_resources or find_resource_deployment (live tools).
            - fact_cost_daily(project_id, wbs_id, activity_id, date, cost_account_id, labor_cost, material_cost, equipment_cost, expense_cost, total_actual, total_planned, total_earned, event_ts)
            - fact_evm_daily(project_id, wbs_id, activity_id, date, bac, pv, ev, ac, cv, sv, cpi, spi, tcpi, eac, etc_cost, vac, period_source, interpolation, event_ts)
            - fact_dpr_logs(project_id, activity_id, dpr_id, report_date, supervisor_user_id, supervisor_name, chainage_from_m, chainage_to_m, qty_executed, cumulative_qty, weather, temperature_c, remarks_text, event_ts)
              -- WARNING: column name supervisor_user_id is legacy; the value is the supervisor's RESOURCE id (FK to dim_resource.resource_id). Filter by it for "DPRs filed by supervisor X". Equality compare to a UUID literal.
            - fact_dpr_manpower_daily(project_id, activity_id, dpr_id, manpower_row_id, report_date, trade, category, contractor_name, nos, working_hours, ot_hours, event_ts)
            - fact_dpr_equipment_daily(project_id, activity_id, dpr_id, equipment_row_id, report_date, equipment_type, fleet_no, ownership, nos, working_hours, idle_hours, breakdown_hours, fuel_litres, operator_name, availability_status, event_ts)
            - fact_dpr_material_daily(project_id, activity_id, dpr_id, material_row_id, report_date, material_name, unit, quantity, source, vendor_name, batch_no, event_ts)
            - fact_risk_snapshot_daily(project_id, risk_id, date, probability, impact_cost, impact_days, rag, status, monte_carlo_p50, monte_carlo_p80, monte_carlo_p95, risk_score, residual_risk_score, risk_type, owner_id, category_id, post_response_probability, post_response_impact_cost, post_response_impact_schedule, pre_response_exposure_cost, post_response_exposure_cost, exposure_start_date, exposure_finish_date, response_type, trend, identified_date, identified_by_id, event_ts)
            - fact_permit_lifecycle(project_id, permit_id, permit_type_template_id, event_type, occurred_at, occurred_date, actor_user_id, risk_level, permit_status, payload_json, duration_hours_to_event, event_ts)
            - fact_labour_daily(project_id, labour_return_id, deployment_id, designation_id, skill_category, contractor_name, contractor_org_id, wbs_id, site_location, date, head_count, man_days, planned_head_count, daily_rate, daily_cost, source, event_ts)

            Materialized views (use sumMerge / maxMerge on *_state cols):
            - mv_project_kpi_daily(project_id, date, ac, pv, ev, rows)
            - mv_portfolio_scurve_weekly(portfolio_id, week_start, pv_state, ev_state, ac_state)
            - mv_activity_weekly(project_id, activity_id, week_start, pct_state, qty_state)
            """;

    public String full() {
        return FULL;
    }

    public String forTable(String table) {
        if (table == null || table.isBlank()) {
            return FULL;
        }
        String needle = table.trim().toLowerCase();
        for (String line : FULL.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- " + needle + "(")) {
                return trimmed;
            }
        }
        return "Table not in schema: " + table + ". Call describe_schema with no argument to see all tables.";
    }
}
