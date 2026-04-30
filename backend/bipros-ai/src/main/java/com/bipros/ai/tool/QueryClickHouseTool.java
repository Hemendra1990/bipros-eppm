package com.bipros.ai.tool;

import com.bipros.ai.context.AiContext;
import com.bipros.analytics.query.ClickHouseQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryClickHouseTool extends ProjectScopedTool {

    private final ClickHouseQueryService queryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "query_clickhouse";
    }

    @Override
    public String description() {
        return """
                Read-only SQL against the analytics warehouse (ClickHouse, database `bipros_analytics`). \
                SELECT only. Every query MUST include a `project_id` filter (use IN for multi-project). \
                Use only columns listed below — column names not in this schema will fail.

                Dimensions:
                - dim_project(project_id, code, name, status, portfolio_id, org_id, start_date, finish_date, currency, obs_node_id, updated_at)
                - dim_wbs(wbs_id, project_id, parent_wbs_id, code, name, level, weight, path)
                - dim_activity(activity_id, project_id, wbs_id, code, name, activity_type, uom, bq_quantity, planned_start, planned_finish, chainage_from_m, chainage_to_m, is_critical)
                - dim_resource(resource_id, project_id, resource_type, code, name, uom, unit_rate, is_subcontractor)
                - dim_cost_account(cost_account_id, project_id, code, name, parent_id, category)
                - dim_calendar(date, year, quarter, month, week, iso_week, day_of_week, is_business_day, fiscal_period)
                - dim_risk(risk_id, project_id, code, title, risk_type, category_id, category_name, owner_id, owner_name, status, rag, trend, response_type, identified_date, identified_by_id, closed_date)
                - dim_permit_type(permit_type_template_id, code, name, color_hex, icon_key, max_duration_hours, requires_gas_test, requires_isolation, jsa_required, blasting_required, diving_required, default_risk_level, night_work_policy)
                - dim_permit(permit_id, project_id, permit_code, permit_type_template_id, parent_permit_id, status, risk_level, shift, contractor_org_id, location_zone, chainage_marker, supervisor_name, start_at, end_at, valid_from, valid_to, declaration_accepted_at, closed_at, closed_by, revoked_at, revoked_by, expired_at, suspended_at, total_approvals_required, approvals_completed)
                - dim_labour_designation(designation_id, code, designation, category, trade, grade, nationality, experience_years_min, default_daily_rate, skills, certifications, status)

                Facts (date-partitioned):
                - fact_activity_progress_daily(project_id, activity_id, date, pct_complete_physical, pct_complete_duration, qty_executed, cumulative_qty, chainage_from_m, chainage_to_m, source, event_ts)
                - fact_resource_usage_daily(project_id, activity_id, resource_id, resource_type, date, hours_worked, days_worked, qty_executed, productivity_actual, productivity_norm, cost, event_ts)
                - fact_cost_daily(project_id, wbs_id, activity_id, date, cost_account_id, labor_cost, material_cost, equipment_cost, expense_cost, total_actual, total_planned, total_earned, event_ts)
                - fact_evm_daily(project_id, wbs_id, activity_id, date, bac, pv, ev, ac, cv, sv, cpi, spi, tcpi, eac, etc_cost, vac, period_source, interpolation, event_ts)
                - fact_dpr_logs(project_id, activity_id, dpr_id, report_date, supervisor_user_id, supervisor_name, chainage_from_m, chainage_to_m, qty_executed, cumulative_qty, weather, temperature_c, remarks_text, event_ts)
                - fact_risk_snapshot_daily(project_id, risk_id, date, probability, impact_cost, impact_days, rag, status, monte_carlo_p50, monte_carlo_p80, monte_carlo_p95, risk_score, residual_risk_score, risk_type, owner_id, category_id, post_response_probability, post_response_impact_cost, post_response_impact_schedule, pre_response_exposure_cost, post_response_exposure_cost, exposure_start_date, exposure_finish_date, response_type, trend, identified_date, identified_by_id, event_ts)
                - fact_permit_lifecycle(project_id, permit_id, permit_type_template_id, event_type, occurred_at, occurred_date, actor_user_id, risk_level, permit_status, payload_json, duration_hours_to_event, event_ts)
                - fact_labour_daily(project_id, labour_return_id, deployment_id, designation_id, skill_category, contractor_name, contractor_org_id, wbs_id, site_location, date, head_count, man_days, planned_head_count, daily_rate, daily_cost, source, event_ts)

                Materialized views:
                - mv_project_kpi_daily(project_id, date, ac, pv, ev, rows)
                - mv_portfolio_scurve_weekly(portfolio_id, week_start, pv_state, ev_state, ac_state)  // use sumMerge() on *_state cols
                - mv_activity_weekly(project_id, activity_id, week_start, pct_state, qty_state)  // use maxMerge(pct_state), sumMerge(qty_state)
                """;
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = objectMapper.createObjectNode();
        props.put("sql", new ObjectMapper().createObjectNode().put("type", "string"));
        props.put("row_limit", new ObjectMapper().createObjectNode().put("type", "integer").put("minimum", 1).put("maximum", 5000));
        schema.set("properties", props);
        ArrayNode req = objectMapper.createArrayNode();
        req.add("sql");
        schema.set("required", req);
        return schema;
    }

    @Override
    protected ToolResult doExecute(JsonNode input, AiContext ctx) {
        String sql = input.path("sql").asText();
        int rowLimit = input.path("row_limit").asInt(500);
        try {
            ClickHouseQueryService.QueryResult result = queryService.runGuarded(sql, ctx.scopedProjectIds(), rowLimit);
            return ToolResult.table("Query returned " + result.rowCount() + " rows" +
                    (result.truncated() ? " (truncated)" : ""), result.rows(), new String[]{"rows"});
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.warn("query_clickhouse failed: {} (root cause: {}: {})",
                    e.getMessage(), root.getClass().getSimpleName(), root.getMessage(), e);
            return ToolResult.error(root.getMessage() != null ? root.getMessage() : e.getMessage());
        }
    }
}
