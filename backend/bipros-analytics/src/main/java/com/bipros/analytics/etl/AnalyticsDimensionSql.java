package com.bipros.analytics.etl;

import com.bipros.activity.domain.model.Activity;
import com.bipros.baseline.domain.Baseline;
import com.bipros.contract.domain.model.VariationOrder;
import com.bipros.cost.domain.entity.CostAccount;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.resource.domain.model.Resource;
import com.bipros.scheduling.domain.model.ScheduleResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared SQL templates + per-row param builders for ClickHouse dimension tables.
 * Both the nightly {@code DimensionSyncJob} and the live event listeners write through
 * these helpers so that {@code ReplacingMergeTree(_version)} dedupes cleanly: a live
 * upsert with {@code _version = currentTimeMillis()} always beats a batch row whose
 * version is fixed at JVM start.
 *
 * <p>Keep this class side-effect free — it must not call ClickHouse directly. Callers
 * pass the {@code (sql, params, version)} triplet into their existing
 * {@code ClickHouseTemplate.execute(...)} path.
 */
public final class AnalyticsDimensionSql {

    private AnalyticsDimensionSql() {}

    // ────────────────────────────────────────────────────────────────────────────
    // dim_project
    // ────────────────────────────────────────────────────────────────────────────

    public static final String INSERT_PROJECT = """
        INSERT INTO bipros_analytics.dim_project
        (project_id, code, name, status, portfolio_id, org_id, start_date, finish_date, currency, obs_node_id, updated_at, _version)
        VALUES (:projectId, :code, :name, :status, :portfolioId, :orgId, :startDate, :finishDate, :currency, :obsNodeId, now(), :version)
        """;

    public static Map<String, Object> projectParams(Project p, long version) {
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", p.getId());
        params.put("code", p.getCode());
        params.put("name", p.getName());
        params.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        params.put("portfolioId", null);
        params.put("orgId", null);
        params.put("startDate", p.getPlannedStartDate());
        params.put("finishDate", p.getPlannedFinishDate());
        params.put("currency", "INR");
        params.put("obsNodeId", p.getObsNodeId());
        params.put("version", version);
        return params;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // dim_wbs
    // ────────────────────────────────────────────────────────────────────────────

    public static final String INSERT_WBS = """
        INSERT INTO bipros_analytics.dim_wbs
        (wbs_id, project_id, parent_wbs_id, code, name, level, weight, path, _version)
        VALUES (:wbsId, :projectId, :parentId, :code, :name, :level, :weight, :path, :version)
        """;

    public static Map<String, Object> wbsParams(WbsNode n, long version) {
        Map<String, Object> params = new HashMap<>();
        params.put("wbsId", n.getId());
        params.put("projectId", n.getProjectId());
        params.put("parentId", n.getParentId());
        params.put("code", n.getCode());
        params.put("name", n.getName());
        params.put("level", n.getWbsLevel() != null ? n.getWbsLevel() : 0);
        params.put("weight", 1.0);
        params.put("path", n.getCode());
        params.put("version", version);
        return params;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // dim_activity
    // ────────────────────────────────────────────────────────────────────────────

    public static final String INSERT_ACTIVITY = """
        INSERT INTO bipros_analytics.dim_activity
        (activity_id, project_id, wbs_id, code, name, activity_type, uom, bq_quantity, planned_start, planned_finish,
         chainage_from_m, chainage_to_m, is_critical,
         responsible_resource_id, responsible_resource_name, _version)
        VALUES (:activityId, :projectId, :wbsId, :code, :name, :activityType, :uom, :bqQty,
                :plannedStart, :plannedFinish, :chainageFrom, :chainageTo, :isCritical,
                :responsibleResourceId, :responsibleResourceName, :version)
        """;

    public static Map<String, Object> activityParams(Activity a, long version) {
        Map<String, Object> params = new HashMap<>();
        params.put("activityId", a.getId());
        params.put("projectId", a.getProjectId());
        params.put("wbsId", a.getWbsNodeId());
        params.put("code", a.getCode() != null ? a.getCode() : "");
        params.put("name", a.getName() != null ? a.getName() : "");
        // CH columns activity_type, uom, bq_quantity are non-nullable. Coalesce
        // before insert so the sync doesn't abort when OLTP rows have nulls.
        params.put("activityType", a.getActivityType() != null ? a.getActivityType().name() : "");
        params.put("uom", "");
        params.put("bqQty", 0.0);
        params.put("plannedStart", a.getPlannedStartDate());
        params.put("plannedFinish", a.getPlannedFinishDate());
        params.put("chainageFrom", a.getChainageFromM() != null ? a.getChainageFromM().doubleValue() : null);
        params.put("chainageTo", a.getChainageToM() != null ? a.getChainageToM().doubleValue() : null);
        params.put("isCritical", a.getIsCritical() != null && a.getIsCritical() ? 1 : 0);
        params.put("responsibleResourceId", a.getResponsibleResourceId());
        params.put("responsibleResourceName",
                a.getResponsibleResourceName() != null ? a.getResponsibleResourceName() : "");
        params.put("version", version);
        return params;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // dim_resource
    // ────────────────────────────────────────────────────────────────────────────

    public static final String INSERT_RESOURCE = """
        INSERT INTO bipros_analytics.dim_resource
        (resource_id, project_id, resource_type, role_code, role_name,
         code, name, uom, unit_rate, is_subcontractor, _version)
        VALUES (:resourceId, :projectId, :resourceType, :roleCode, :roleName,
                :code, :name, :uom, :unitRate, :isSubcontractor, :version)
        """;

    public static Map<String, Object> resourceParams(Resource r, long version) {
        Map<String, Object> params = new HashMap<>();
        params.put("resourceId", r.getId());
        params.put("projectId", null);
        // CH non-nullable columns — coalesce so the sync doesn't abort when
        // OLTP rows have nulls (e.g. a resource not yet linked to a rate master).
        params.put("resourceType", r.getResourceType() != null ? r.getResourceType().getCode() : "");
        // role_code/role_name denormalised so AI supervisor queries can filter
        // by role without joining back to Postgres. Empty string when role is null.
        params.put("roleCode", r.getRole() == null ? "" : (r.getRole().getCode() == null ? "" : r.getRole().getCode()));
        params.put("roleName", r.getRole() == null ? "" : (r.getRole().getName() == null ? "" : r.getRole().getName()));
        params.put("code", r.getCode() != null ? r.getCode() : "");
        params.put("name", r.getName() != null ? r.getName() : "");
        params.put("uom", r.getUnit() != null ? r.getUnit() : "");
        params.put("unitRate", r.getCostPerUnit() != null ? r.getCostPerUnit().doubleValue() : 0.0);
        params.put("isSubcontractor", 0);
        params.put("version", version);
        return params;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // dim_cost_account
    // ────────────────────────────────────────────────────────────────────────────

    public static final String INSERT_COST_ACCOUNT = """
        INSERT INTO bipros_analytics.dim_cost_account
        (cost_account_id, project_id, code, name, parent_id, category, _version)
        VALUES (:costAccountId, :projectId, :code, :name, :parentId, :category, :version)
        """;

    public static Map<String, Object> costAccountParams(CostAccount ca, long version) {
        Map<String, Object> params = new HashMap<>();
        params.put("costAccountId", ca.getId());
        // CH `project_id` on dim_cost_account is non-nullable per init SQL.
        // OLTP CostAccount has no project_id today (cost accounts are global) —
        // emit a sentinel zero UUID instead of null.
        params.put("projectId", new UUID(0L, 0L));
        params.put("code", ca.getCode() != null ? ca.getCode() : "");
        params.put("name", ca.getName() != null ? ca.getName() : "");
        params.put("parentId", ca.getParentId());
        params.put("category", "");
        params.put("version", version);
        return params;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // dim_baseline
    // ────────────────────────────────────────────────────────────────────────────

    public static final String INSERT_BASELINE = """
        INSERT INTO bipros_analytics.dim_baseline
        (baseline_id, project_id, name, description, baseline_type, baseline_date,
         is_active, total_activities, total_cost, project_duration,
         project_start_date, project_finish_date, updated_at, _version)
        VALUES (:baselineId, :projectId, :name, :description, :baselineType, :baselineDate,
                :isActive, :totalActivities, :totalCost, :projectDuration,
                :projectStartDate, :projectFinishDate, now(), :version)
        """;

    public static Map<String, Object> baselineParams(Baseline b, boolean active, long version) {
        Map<String, Object> params = new HashMap<>();
        params.put("baselineId", b.getId());
        params.put("projectId", b.getProjectId());
        params.put("name", b.getName() != null ? b.getName() : "");
        params.put("description", b.getDescription() != null ? b.getDescription() : "");
        params.put("baselineType", b.getBaselineType() != null ? b.getBaselineType().name() : "");
        params.put("baselineDate", b.getBaselineDate());
        params.put("isActive", active ? 1 : 0);
        params.put("totalActivities", b.getTotalActivities());
        params.put("totalCost", b.getTotalCost());
        params.put("projectDuration", b.getProjectDuration());
        params.put("projectStartDate", b.getProjectStartDate());
        params.put("projectFinishDate", b.getProjectFinishDate());
        params.put("version", version);
        return params;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // dim_schedule_run
    // ────────────────────────────────────────────────────────────────────────────

    public static final String INSERT_SCHEDULE_RUN = """
        INSERT INTO bipros_analytics.dim_schedule_run
        (schedule_run_id, project_id, data_date, project_start_date, project_finish_date,
         critical_path_length, total_activities, critical_activities,
         scheduling_option, status, duration_seconds, calculated_at, _version)
        VALUES (:scheduleRunId, :projectId, :dataDate, :projectStartDate, :projectFinishDate,
                :criticalPathLength, :totalActivities, :criticalActivities,
                :schedulingOption, :status, :durationSeconds, :calculatedAt, :version)
        """;

    public static Map<String, Object> scheduleRunParams(ScheduleResult s, long version) {
        Map<String, Object> params = new HashMap<>();
        params.put("scheduleRunId", s.getId());
        params.put("projectId", s.getProjectId());
        params.put("dataDate", s.getDataDate());
        params.put("projectStartDate", s.getProjectStartDate());
        params.put("projectFinishDate", s.getProjectFinishDate());
        params.put("criticalPathLength", s.getCriticalPathLength());
        params.put("totalActivities", s.getTotalActivities());
        params.put("criticalActivities", s.getCriticalActivities());
        params.put("schedulingOption", s.getSchedulingOption() != null ? s.getSchedulingOption().name() : "");
        params.put("status", s.getStatus() != null ? s.getStatus().name() : "");
        params.put("durationSeconds", s.getDurationSeconds());
        params.put("calculatedAt", s.getCalculatedAt());
        params.put("version", version);
        return params;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // dim_contract (Variation Order)
    // ────────────────────────────────────────────────────────────────────────────

    public static final String INSERT_CONTRACT = """
        INSERT INTO bipros_analytics.dim_contract
        (vo_id, contract_id, project_id, vo_number, description, vo_value,
         impact_on_budget, impact_on_schedule_days, status, approved_by, approved_at,
         updated_at, _version)
        VALUES (:voId, :contractId, :projectId, :voNumber, :description, :voValue,
                :impactOnBudget, :impactOnScheduleDays, :status, :approvedBy, :approvedAt,
                now(), :version)
        """;

    public static Map<String, Object> contractParams(VariationOrder vo, UUID projectId, long version) {
        Map<String, Object> params = new HashMap<>();
        params.put("voId", vo.getId());
        params.put("contractId", vo.getContractId());
        params.put("projectId", projectId);
        params.put("voNumber", vo.getVoNumber() != null ? vo.getVoNumber() : "");
        params.put("description", vo.getDescription() != null ? vo.getDescription() : "");
        params.put("voValue", vo.getVoValue());
        params.put("impactOnBudget", vo.getImpactOnBudget());
        params.put("impactOnScheduleDays", vo.getImpactOnScheduleDays());
        params.put("status", vo.getStatus() != null ? vo.getStatus().name() : "");
        params.put("approvedBy", vo.getApprovedBy() != null ? vo.getApprovedBy() : "");
        params.put("approvedAt", vo.getApprovedAt());
        params.put("version", version);
        return params;
    }
}
