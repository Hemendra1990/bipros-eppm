package com.bipros.analytics.etl;

import com.bipros.analytics.etl.dto.RiskSnapshotRow;
import com.bipros.analytics.store.ClickHouseTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central service for streaming inserts into ClickHouse fact tables.
 * All methods are idempotent via ReplacingMergeTree(_version).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsEtlService {

    private final ClickHouseTemplate clickHouse;

    private long nowVersion() {
        return System.currentTimeMillis();
    }

    public void insertActivityProgressDaily(
            UUID projectId, UUID activityId, LocalDate date,
            Float pctCompletePhysical, Float pctCompleteDuration,
            Double qtyExecuted, Double cumulativeQty,
            Double chainageFromM, Double chainageToM,
            String source) {

        String sql = """
            INSERT INTO bipros_analytics.fact_activity_progress_daily
            (project_id, activity_id, date, pct_complete_physical, pct_complete_duration,
             qty_executed, cumulative_qty, chainage_from_m, chainage_to_m, source, event_ts, _version)
            VALUES (:projectId, :activityId, :date, :pctPhysical, :pctDuration,
                    :qtyExecuted, :cumulativeQty, :chainageFrom, :chainageTo, :source, now64(3), :version)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("activityId", activityId);
        params.put("date", date);
        params.put("pctPhysical", pctCompletePhysical);
        params.put("pctDuration", pctCompleteDuration);
        params.put("qtyExecuted", qtyExecuted);
        params.put("cumulativeQty", cumulativeQty);
        params.put("chainageFrom", chainageFromM);
        params.put("chainageTo", chainageToM);
        params.put("source", source);
        params.put("version", nowVersion());

        clickHouse.execute(sql, params);
        log.debug("Inserted activity_progress: project={} activity={} date={}", projectId, activityId, date);
    }

    public void insertResourceUsageDaily(
            UUID projectId, UUID activityId, UUID resourceId, String resourceType, LocalDate date,
            Float hoursWorked, Float daysWorked, Double qtyExecuted,
            Float productivityActual, Float productivityNorm, BigDecimal cost) {

        String sql = """
            INSERT INTO bipros_analytics.fact_resource_usage_daily
            (project_id, activity_id, resource_id, resource_type, date,
             hours_worked, days_worked, qty_executed, productivity_actual, productivity_norm, cost, event_ts, _version)
            VALUES (:projectId, :activityId, :resourceId, :resourceType, :date,
                    :hoursWorked, :daysWorked, :qtyExecuted, :prodActual, :prodNorm, :cost, now64(3), :version)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("activityId", activityId);
        params.put("resourceId", resourceId);
        params.put("resourceType", resourceType);
        params.put("date", date);
        params.put("hoursWorked", hoursWorked);
        params.put("daysWorked", daysWorked);
        params.put("qtyExecuted", qtyExecuted);
        params.put("prodActual", productivityActual);
        params.put("prodNorm", productivityNorm);
        params.put("cost", cost);
        params.put("version", nowVersion());

        clickHouse.execute(sql, params);
        log.debug("Inserted resource_usage: project={} activity={} resource={} date={}", projectId, activityId, resourceId, date);
    }

    public void insertCostDaily(
            UUID projectId, UUID wbsId, UUID activityId, LocalDate date, UUID costAccountId,
            BigDecimal laborCost, BigDecimal materialCost, BigDecimal equipmentCost, BigDecimal expenseCost,
            BigDecimal totalActual, BigDecimal totalPlanned, BigDecimal totalEarned) {

        String sql = """
            INSERT INTO bipros_analytics.fact_cost_daily
            (project_id, wbs_id, activity_id, date, cost_account_id,
             labor_cost, material_cost, equipment_cost, expense_cost,
             total_actual, total_planned, total_earned, event_ts, _version)
            VALUES (:projectId, :wbsId, :activityId, :date, :costAccountId,
                    :laborCost, :materialCost, :equipmentCost, :expenseCost,
                    :totalActual, :totalPlanned, :totalEarned, now64(3), :version)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("wbsId", wbsId);
        params.put("activityId", activityId);
        params.put("date", date);
        params.put("costAccountId", costAccountId);
        params.put("laborCost", laborCost);
        params.put("materialCost", materialCost);
        params.put("equipmentCost", equipmentCost);
        params.put("expenseCost", expenseCost);
        params.put("totalActual", totalActual);
        params.put("totalPlanned", totalPlanned);
        params.put("totalEarned", totalEarned);
        params.put("version", nowVersion());

        clickHouse.execute(sql, params);
        log.debug("Inserted cost_daily: project={} activity={} date={}", projectId, activityId, date);
    }

    public void insertEvmDaily(
            UUID projectId, UUID wbsId, UUID activityId, LocalDate date,
            BigDecimal bac, BigDecimal pv, BigDecimal ev, BigDecimal ac,
            BigDecimal cv, BigDecimal sv, Double cpi, Double spi, Double tcpi,
            BigDecimal eac, BigDecimal etcCost, BigDecimal vac,
            String periodSource, String interpolation) {

        String sql = """
            INSERT INTO bipros_analytics.fact_evm_daily
            (project_id, wbs_id, activity_id, date, bac, pv, ev, ac, cv, sv, cpi, spi, tcpi,
             eac, etc_cost, vac, period_source, interpolation, event_ts, _version)
            VALUES (:projectId, :wbsId, :activityId, :date, :bac, :pv, :ev, :ac, :cv, :sv, :cpi, :spi, :tcpi,
                    :eac, :etcCost, :vac, :periodSource, :interpolation, now64(3), :version)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("wbsId", wbsId);
        params.put("activityId", activityId);
        params.put("date", date);
        params.put("bac", bac);
        params.put("pv", pv);
        params.put("ev", ev);
        params.put("ac", ac);
        params.put("cv", cv);
        params.put("sv", sv);
        params.put("cpi", cpi);
        params.put("spi", spi);
        params.put("tcpi", tcpi);
        params.put("eac", eac);
        params.put("etcCost", etcCost);
        params.put("vac", vac);
        params.put("periodSource", periodSource);
        params.put("interpolation", interpolation);
        params.put("version", nowVersion());

        clickHouse.execute(sql, params);
        log.debug("Inserted evm_daily: project={} date={}", projectId, date);
    }

    public void insertDprLog(
            UUID projectId, UUID activityId, UUID dprId, LocalDate reportDate,
            UUID supervisorUserId, String supervisorName,
            Double chainageFromM, Double chainageToM,
            Double qtyExecuted, Double cumulativeQty,
            String weather, Float temperatureC, String remarksText) {

        String sql = """
            INSERT INTO bipros_analytics.fact_dpr_logs
            (project_id, activity_id, dpr_id, report_date, supervisor_user_id, supervisor_name,
             chainage_from_m, chainage_to_m, qty_executed, cumulative_qty,
             weather, temperature_c, remarks_text, remarks_embedding, event_ts, _version)
            VALUES (:projectId, :activityId, :dprId, :reportDate, :supervisorUserId, :supervisorName,
                    :chainageFrom, :chainageTo, :qtyExecuted, :cumulativeQty,
                    :weather, :temperatureC, :remarksText, [], now64(3), :version)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("activityId", activityId != null ? activityId : new UUID(0L, 0L));
        params.put("dprId", dprId);
        params.put("reportDate", reportDate);
        params.put("supervisorUserId", supervisorUserId != null ? supervisorUserId : new UUID(0L, 0L));
        params.put("supervisorName", supervisorName != null ? supervisorName : "");
        params.put("chainageFrom", chainageFromM);
        params.put("chainageTo", chainageToM);
        params.put("qtyExecuted", qtyExecuted != null ? qtyExecuted : 0.0);
        params.put("cumulativeQty", cumulativeQty != null ? cumulativeQty : 0.0);
        params.put("weather", weather != null ? weather : "");
        params.put("temperatureC", temperatureC);
        params.put("remarksText", remarksText != null ? remarksText : "");
        params.put("version", nowVersion());

        clickHouse.execute(sql, params);
        log.debug("Inserted dpr_log: project={} dpr={} date={}", projectId, dprId, reportDate);
    }

    public void insertRiskSnapshotDaily(
            UUID projectId, UUID riskId, LocalDate date,
            Float probability, BigDecimal impactCost, Integer impactDays,
            String rag, String status,
            BigDecimal mcP50, BigDecimal mcP80, BigDecimal mcP95) {
        insertRiskSnapshotDaily(RiskSnapshotRow.builder()
                .projectId(projectId).riskId(riskId).date(date)
                .probability(probability).impactCost(impactCost).impactDays(impactDays)
                .rag(rag).status(status)
                .monteCarloP50(mcP50).monteCarloP80(mcP80).monteCarloP95(mcP95)
                .build());
    }

    public void insertRiskSnapshotDaily(RiskSnapshotRow row) {
        String sql = """
            INSERT INTO bipros_analytics.fact_risk_snapshot_daily
            (project_id, risk_id, date, probability, impact_cost, impact_days,
             rag, status, monte_carlo_p50, monte_carlo_p80, monte_carlo_p95,
             risk_score, residual_risk_score, risk_type, owner_id, category_id,
             post_response_probability, post_response_impact_cost, post_response_impact_schedule,
             pre_response_exposure_cost, post_response_exposure_cost,
             exposure_start_date, exposure_finish_date,
             response_type, trend, identified_date, identified_by_id,
             event_ts, _version)
            VALUES (:projectId, :riskId, :date, :probability, :impactCost, :impactDays,
                    :rag, :status, :mcP50, :mcP80, :mcP95,
                    :riskScore, :residualRiskScore, :riskType, :ownerId, :categoryId,
                    :postProbability, :postImpactCost, :postImpactSchedule,
                    :preExposureCost, :postExposureCost,
                    :exposureStart, :exposureFinish,
                    :responseType, :trend, :identifiedDate, :identifiedById,
                    now64(3), :version)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", row.projectId());
        params.put("riskId", row.riskId());
        params.put("date", row.date());
        params.put("probability", row.probability());
        params.put("impactCost", row.impactCost());
        params.put("impactDays", row.impactDays());
        params.put("rag", emptyIfNull(row.rag()));
        params.put("status", emptyIfNull(row.status()));
        params.put("mcP50", row.monteCarloP50());
        params.put("mcP80", row.monteCarloP80());
        params.put("mcP95", row.monteCarloP95());
        params.put("riskScore", row.riskScore());
        params.put("residualRiskScore", row.residualRiskScore());
        params.put("riskType", row.riskType() != null ? row.riskType() : "THREAT");
        params.put("ownerId", row.ownerId());
        params.put("categoryId", row.categoryId());
        params.put("postProbability", row.postResponseProbability());
        params.put("postImpactCost", row.postResponseImpactCost());
        params.put("postImpactSchedule", row.postResponseImpactSchedule());
        params.put("preExposureCost", row.preResponseExposureCost());
        params.put("postExposureCost", row.postResponseExposureCost());
        params.put("exposureStart", row.exposureStartDate());
        params.put("exposureFinish", row.exposureFinishDate());
        params.put("responseType", emptyIfNull(row.responseType()));
        params.put("trend", emptyIfNull(row.trend()));
        params.put("identifiedDate", row.identifiedDate());
        params.put("identifiedById", row.identifiedById());
        params.put("version", nowVersion());

        clickHouse.execute(sql, params);
        log.debug("Inserted risk_snapshot: project={} risk={} date={}",
                row.projectId(), row.riskId(), row.date());
    }

    public void insertPermitLifecycle(
            UUID projectId, UUID permitId, UUID permitTypeTemplateId,
            String eventType, Instant occurredAt, UUID actorUserId,
            String riskLevel, String permitStatus, String payloadJson,
            Float durationHoursToEvent) {

        String sql = """
            INSERT INTO bipros_analytics.fact_permit_lifecycle
            (project_id, permit_id, permit_type_template_id, event_type, occurred_at,
             actor_user_id, risk_level, permit_status, payload_json,
             duration_hours_to_event, event_ts, _version)
            VALUES (:projectId, :permitId, :typeTemplateId, :eventType, :occurredAt,
                    :actorUserId, :riskLevel, :permitStatus, :payloadJson,
                    :durationHours, now64(3), :version)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("permitId", permitId);
        params.put("typeTemplateId", permitTypeTemplateId);
        params.put("eventType", eventType);
        params.put("occurredAt", occurredAt);
        params.put("actorUserId", actorUserId);
        params.put("riskLevel", emptyIfNull(riskLevel));
        params.put("permitStatus", emptyIfNull(permitStatus));
        params.put("payloadJson", payloadJson != null ? payloadJson : "");
        params.put("durationHours", durationHoursToEvent);
        params.put("version", nowVersion());

        clickHouse.execute(sql, params);
        log.debug("Inserted permit_lifecycle: project={} permit={} event={} at={}",
                projectId, permitId, eventType, occurredAt);
    }

    public void insertLabourDaily(
            UUID projectId, UUID labourReturnId, UUID deploymentId, UUID designationId,
            String skillCategory, String contractorName, UUID contractorOrgId,
            UUID wbsId, String siteLocation, LocalDate date,
            Integer headCount, Float manDays, Integer plannedHeadCount,
            BigDecimal dailyRate, BigDecimal dailyCost, String source) {

        String sql = """
            INSERT INTO bipros_analytics.fact_labour_daily
            (project_id, labour_return_id, deployment_id, designation_id,
             skill_category, contractor_name, contractor_org_id, wbs_id, site_location,
             date, head_count, man_days, planned_head_count,
             daily_rate, daily_cost, source, event_ts, _version)
            VALUES (:projectId, :labourReturnId, :deploymentId, :designationId,
                    :skillCategory, :contractorName, :contractorOrgId, :wbsId, :siteLocation,
                    :date, :headCount, :manDays, :plannedHeadCount,
                    :dailyRate, :dailyCost, :source, now64(3), :version)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("labourReturnId", labourReturnId);
        params.put("deploymentId", deploymentId);
        params.put("designationId", designationId);
        params.put("skillCategory", emptyIfNull(skillCategory));
        params.put("contractorName", contractorName != null ? contractorName : "");
        params.put("contractorOrgId", contractorOrgId);
        params.put("wbsId", wbsId);
        params.put("siteLocation", siteLocation != null ? siteLocation : "");
        params.put("date", date);
        params.put("headCount", headCount != null ? headCount : 0);
        params.put("manDays", manDays != null ? manDays : 0f);
        params.put("plannedHeadCount", plannedHeadCount);
        params.put("dailyRate", dailyRate);
        params.put("dailyCost", dailyCost);
        params.put("source", source);
        params.put("version", nowVersion());

        clickHouse.execute(sql, params);
        log.debug("Inserted labour_daily: project={} date={} contractor={} skill={} source={}",
                projectId, date, contractorName, skillCategory, source);
    }

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }
}
