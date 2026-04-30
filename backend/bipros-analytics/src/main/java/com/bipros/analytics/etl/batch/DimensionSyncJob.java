package com.bipros.analytics.etl.batch;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.analytics.store.ClickHouseTemplate;
import com.bipros.common.scheduling.ScheduledJobLeaseRepository;
import com.bipros.cost.domain.entity.CostAccount;
import com.bipros.cost.domain.repository.CostAccountRepository;
import com.bipros.permit.domain.model.Permit;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.repository.PermitRepository;
import com.bipros.permit.domain.repository.PermitTypeTemplateRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.resource.domain.model.LabourDesignation;
import com.bipros.resource.domain.model.ProjectLabourDeployment;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.LabourDesignationRepository;
import com.bipros.resource.domain.repository.ProjectLabourDeploymentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.risk.domain.model.Risk;
import com.bipros.risk.domain.repository.RiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nightly full refresh of ClickHouse dimension tables from Postgres.
 * Uses ReplacingMergeTree(_version) so duplicates are automatically deduped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DimensionSyncJob {

    private static final String JOB_NAME = "analytics_dimension_sync";
    private static final long VERSION = System.currentTimeMillis();

    private final ScheduledJobLeaseRepository leaseRepository;
    private final ClickHouseTemplate clickHouse;

    private final ProjectRepository projectRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final ActivityRepository activityRepository;
    private final ResourceRepository resourceRepository;
    private final CostAccountRepository costAccountRepository;
    private final RiskRepository riskRepository;
    private final PermitRepository permitRepository;
    private final PermitTypeTemplateRepository permitTypeTemplateRepository;
    private final LabourDesignationRepository labourDesignationRepository;
    private final ProjectLabourDeploymentRepository projectLabourDeploymentRepository;

    @Scheduled(cron = "0 30 1 * * *")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        Instant until = now.plusSeconds(600);
        String owner = "node-" + UUID.randomUUID();
        if (leaseRepository.tryAcquire(JOB_NAME, until, now, owner) == 0) {
            log.debug("DimensionSyncJob skipped — another node holds the lease");
            return;
        }

        long start = System.currentTimeMillis();
        syncProjects();
        syncWbs();
        syncActivities();
        syncResources();
        syncCostAccounts();
        syncCalendar();
        syncRisks();
        syncPermitTypeTemplates();
        syncPermits();
        syncLabourDesignations();
        syncLabourDeploymentSnapshot();
        log.info("DimensionSyncJob completed in {} ms", System.currentTimeMillis() - start);
    }

    private void syncProjects() {
        List<Project> projects = projectRepository.findAll();
        String sql = """
            INSERT INTO bipros_analytics.dim_project
            (project_id, code, name, status, portfolio_id, org_id, start_date, finish_date, currency, obs_node_id, updated_at, _version)
            VALUES (:projectId, :code, :name, :status, :portfolioId, :orgId, :startDate, :finishDate, :currency, :obsNodeId, now(), :version)
            """;
        for (Project p : projects) {
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
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} projects", projects.size());
    }

    private void syncWbs() {
        List<WbsNode> nodes = wbsNodeRepository.findAll();
        String sql = """
            INSERT INTO bipros_analytics.dim_wbs
            (wbs_id, project_id, parent_wbs_id, code, name, level, weight, path, _version)
            VALUES (:wbsId, :projectId, :parentId, :code, :name, :level, :weight, :path, :version)
            """;
        for (WbsNode n : nodes) {
            Map<String, Object> params = new HashMap<>();
            params.put("wbsId", n.getId());
            params.put("projectId", n.getProjectId());
            params.put("parentId", n.getParentId());
            params.put("code", n.getCode());
            params.put("name", n.getName());
            params.put("level", n.getWbsLevel() != null ? n.getWbsLevel() : 0);
            params.put("weight", 1.0);
            params.put("path", n.getCode());
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} WBS nodes", nodes.size());
    }

    private void syncActivities() {
        List<Activity> activities = activityRepository.findAll();
        String sql = """
            INSERT INTO bipros_analytics.dim_activity
            (activity_id, project_id, wbs_id, code, name, activity_type, uom, bq_quantity, planned_start, planned_finish,
             chainage_from_m, chainage_to_m, is_critical, _version)
            VALUES (:activityId, :projectId, :wbsId, :code, :name, :activityType, :uom, :bqQty,
                    :plannedStart, :plannedFinish, :chainageFrom, :chainageTo, :isCritical, :version)
            """;
        for (Activity a : activities) {
            Map<String, Object> params = new HashMap<>();
            params.put("activityId", a.getId());
            params.put("projectId", a.getProjectId());
            params.put("wbsId", a.getWbsNodeId());
            params.put("code", a.getCode());
            params.put("name", a.getName());
            params.put("activityType", a.getActivityType() != null ? a.getActivityType().name() : null);
            params.put("uom", null);
            params.put("bqQty", null);
            params.put("plannedStart", a.getPlannedStartDate());
            params.put("plannedFinish", a.getPlannedFinishDate());
            params.put("chainageFrom", a.getChainageFromM() != null ? a.getChainageFromM().doubleValue() : null);
            params.put("chainageTo", a.getChainageToM() != null ? a.getChainageToM().doubleValue() : null);
            params.put("isCritical", a.getIsCritical() != null && a.getIsCritical() ? 1 : 0);
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} activities", activities.size());
    }

    private void syncResources() {
        List<Resource> resources = resourceRepository.findAll();
        String sql = """
            INSERT INTO bipros_analytics.dim_resource
            (resource_id, project_id, resource_type, code, name, uom, unit_rate, is_subcontractor, _version)
            VALUES (:resourceId, :projectId, :resourceType, :code, :name, :uom, :unitRate, :isSubcontractor, :version)
            """;
        for (Resource r : resources) {
            Map<String, Object> params = new HashMap<>();
            params.put("resourceId", r.getId());
            params.put("projectId", null);
            params.put("resourceType", r.getResourceType() != null ? r.getResourceType().name() : null);
            params.put("code", r.getCode());
            params.put("name", r.getName());
            params.put("uom", r.getUnit() != null ? r.getUnit().name() : null);
            params.put("unitRate", r.getHourlyRate());
            params.put("isSubcontractor", 0);
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} resources", resources.size());
    }

    private void syncCostAccounts() {
        List<CostAccount> accounts = costAccountRepository.findAll();
        String sql = """
            INSERT INTO bipros_analytics.dim_cost_account
            (cost_account_id, project_id, code, name, parent_id, category, _version)
            VALUES (:costAccountId, :projectId, :code, :name, :parentId, :category, :version)
            """;
        for (CostAccount ca : accounts) {
            Map<String, Object> params = new HashMap<>();
            params.put("costAccountId", ca.getId());
            params.put("projectId", null);
            params.put("code", ca.getCode());
            params.put("name", ca.getName());
            params.put("parentId", ca.getParentId());
            params.put("category", null);
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} cost accounts", accounts.size());
    }

    private void syncCalendar() {
        // Seed calendar for 2020-2030 if empty
        String countSql = "SELECT count() FROM bipros_analytics.dim_calendar";
        List<Map<String, Object>> rows = clickHouse.queryForList(countSql, Map.of());
        long existing = rows.isEmpty() ? 0 : ((Number) rows.get(0).get("count()")).longValue();
        if (existing > 0) {
            return;
        }

        LocalDate date = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2030, 12, 31);
        String sql = """
            INSERT INTO bipros_analytics.dim_calendar
            (date, year, quarter, month, week, iso_week, day_of_week, is_business_day, fiscal_period)
            VALUES (:date, :year, :quarter, :month, :week, :isoWeek, :dayOfWeek, :isBusinessDay, :fiscalPeriod)
            """;
        while (!date.isAfter(end)) {
            Map<String, Object> params = new HashMap<>();
            params.put("date", date);
            params.put("year", date.getYear());
            params.put("quarter", (date.getMonthValue() - 1) / 3 + 1);
            params.put("month", date.getMonthValue());
            params.put("week", date.getDayOfYear() / 7 + 1);
            params.put("isoWeek", date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));
            params.put("dayOfWeek", date.getDayOfWeek().getValue());
            params.put("isBusinessDay", date.getDayOfWeek().getValue() <= 5 ? 1 : 0);
            params.put("fiscalPeriod", date.getMonthValue());
            clickHouse.execute(sql, params);
            date = date.plusDays(1);
        }
        log.debug("Seeded dim_calendar 2020-2030");
    }

    private void syncRisks() {
        List<Risk> risks = riskRepository.findAll();
        String sql = """
            INSERT INTO bipros_analytics.dim_risk
            (risk_id, project_id, code, title, risk_type, category_id, category_name,
             owner_id, owner_name, status, rag, trend, response_type,
             identified_date, identified_by_id, closed_date, _version)
            VALUES (:riskId, :projectId, :code, :title, :riskType, :categoryId, :categoryName,
                    :ownerId, :ownerName, :status, :rag, :trend, :responseType,
                    :identifiedDate, :identifiedById, :closedDate, :version)
            """;
        for (Risk r : risks) {
            Map<String, Object> params = new HashMap<>();
            params.put("riskId", r.getId());
            params.put("projectId", r.getProjectId());
            params.put("code", r.getCode());
            params.put("title", r.getTitle());
            params.put("riskType", r.getRiskType() != null ? r.getRiskType().name() : "THREAT");
            params.put("categoryId", r.getCategory() != null ? r.getCategory().getId() : null);
            params.put("categoryName", r.getCategory() != null ? r.getCategory().getName() : "");
            params.put("ownerId", r.getOwnerId());
            params.put("ownerName", "");
            params.put("status", r.getStatus() != null ? r.getStatus().name() : "");
            params.put("rag", r.getRag() != null ? r.getRag().name() : "");
            params.put("trend", r.getTrend() != null ? r.getTrend().name() : "");
            params.put("responseType", r.getResponseType() != null ? r.getResponseType().name() : "");
            params.put("identifiedDate", r.getIdentifiedDate());
            params.put("identifiedById", r.getIdentifiedById());
            params.put("closedDate", null);
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} risks", risks.size());
    }

    private void syncPermitTypeTemplates() {
        List<PermitTypeTemplate> templates = permitTypeTemplateRepository.findAll();
        String sql = """
            INSERT INTO bipros_analytics.dim_permit_type
            (permit_type_template_id, code, name, color_hex, icon_key, max_duration_hours,
             requires_gas_test, requires_isolation, jsa_required, blasting_required, diving_required,
             default_risk_level, night_work_policy, _version)
            VALUES (:typeId, :code, :name, :colorHex, :iconKey, :maxDurationHours,
                    :requiresGasTest, :requiresIsolation, :jsaRequired, :blastingRequired, :divingRequired,
                    :defaultRiskLevel, :nightWorkPolicy, :version)
            """;
        for (PermitTypeTemplate t : templates) {
            Map<String, Object> params = new HashMap<>();
            params.put("typeId", t.getId());
            params.put("code", t.getCode());
            params.put("name", t.getName());
            params.put("colorHex", t.getColorHex() != null ? t.getColorHex() : "");
            params.put("iconKey", t.getIconKey() != null ? t.getIconKey() : "");
            params.put("maxDurationHours", t.getMaxDurationHours());
            params.put("requiresGasTest", t.isGasTestRequired() ? 1 : 0);
            params.put("requiresIsolation", t.isIsolationRequired() ? 1 : 0);
            params.put("jsaRequired", t.isJsaRequired() ? 1 : 0);
            params.put("blastingRequired", t.isBlastingRequired() ? 1 : 0);
            params.put("divingRequired", t.isDivingRequired() ? 1 : 0);
            params.put("defaultRiskLevel", t.getDefaultRiskLevel() != null ? t.getDefaultRiskLevel().name() : "");
            params.put("nightWorkPolicy", t.getNightWorkPolicy() != null ? t.getNightWorkPolicy().name() : "");
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} permit type templates", templates.size());
    }

    private void syncPermits() {
        List<Permit> permits = permitRepository.findAll();
        String sql = """
            INSERT INTO bipros_analytics.dim_permit
            (permit_id, project_id, permit_code, permit_type_template_id, parent_permit_id,
             status, risk_level, shift, contractor_org_id, location_zone, chainage_marker, supervisor_name,
             start_at, end_at, valid_from, valid_to, declaration_accepted_at,
             closed_at, closed_by, revoked_at, revoked_by, expired_at, suspended_at,
             total_approvals_required, approvals_completed, _version)
            VALUES (:permitId, :projectId, :permitCode, :typeId, :parentPermitId,
                    :status, :riskLevel, :shift, :contractorOrgId, :locationZone, :chainageMarker, :supervisorName,
                    :startAt, :endAt, :validFrom, :validTo, :declarationAcceptedAt,
                    :closedAt, :closedBy, :revokedAt, :revokedBy, :expiredAt, :suspendedAt,
                    :totalApprovalsRequired, :approvalsCompleted, :version)
            """;
        for (Permit p : permits) {
            Map<String, Object> params = new HashMap<>();
            params.put("permitId", p.getId());
            params.put("projectId", p.getProjectId());
            params.put("permitCode", p.getPermitCode());
            params.put("typeId", p.getPermitTypeTemplateId());
            params.put("parentPermitId", p.getParentPermitId());
            params.put("status", p.getStatus() != null ? p.getStatus().name() : "");
            params.put("riskLevel", p.getRiskLevel() != null ? p.getRiskLevel().name() : "");
            params.put("shift", p.getShift() != null ? p.getShift().name() : "");
            params.put("contractorOrgId", p.getContractorOrgId());
            params.put("locationZone", p.getLocationZone() != null ? p.getLocationZone() : "");
            params.put("chainageMarker", p.getChainageMarker() != null ? p.getChainageMarker() : "");
            params.put("supervisorName", p.getSupervisorName() != null ? p.getSupervisorName() : "");
            params.put("startAt", p.getStartAt());
            params.put("endAt", p.getEndAt());
            params.put("validFrom", p.getValidFrom());
            params.put("validTo", p.getValidTo());
            params.put("declarationAcceptedAt", p.getDeclarationAcceptedAt());
            params.put("closedAt", p.getClosedAt());
            params.put("closedBy", p.getClosedBy());
            params.put("revokedAt", p.getRevokedAt());
            params.put("revokedBy", p.getRevokedBy());
            params.put("expiredAt", p.getExpiredAt());
            params.put("suspendedAt", p.getSuspendedAt());
            params.put("totalApprovalsRequired", p.getTotalApprovalsRequired());
            params.put("approvalsCompleted", p.getApprovalsCompleted());
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} permits", permits.size());
    }

    private void syncLabourDesignations() {
        List<LabourDesignation> designations = labourDesignationRepository.findAll();
        String sql = """
            INSERT INTO bipros_analytics.dim_labour_designation
            (designation_id, code, designation, category, trade, grade, nationality,
             experience_years_min, default_daily_rate, skills, certifications, status, _version)
            VALUES (:designationId, :code, :designation, :category, :trade, :grade, :nationality,
                    :experienceYearsMin, :defaultDailyRate, :skills, :certifications, :status, :version)
            """;
        for (LabourDesignation d : designations) {
            Map<String, Object> params = new HashMap<>();
            params.put("designationId", d.getId());
            params.put("code", d.getCode());
            params.put("designation", d.getDesignation());
            params.put("category", d.getCategory() != null ? d.getCategory().name() : "");
            params.put("trade", d.getTrade() != null ? d.getTrade() : "");
            params.put("grade", d.getGrade() != null ? d.getGrade().name() : "");
            params.put("nationality", d.getNationality() != null ? d.getNationality().name() : "");
            params.put("experienceYearsMin", d.getExperienceYearsMin() != null ? d.getExperienceYearsMin() : 0);
            params.put("defaultDailyRate", d.getDefaultDailyRate());
            params.put("skills", d.getSkills() != null ? d.getSkills() : List.of());
            params.put("certifications", d.getCertifications() != null ? d.getCertifications() : List.of());
            params.put("status", d.getStatus() != null ? d.getStatus().name() : "");
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} labour designations", designations.size());
    }

    private void syncLabourDeploymentSnapshot() {
        List<ProjectLabourDeployment> deployments = projectLabourDeploymentRepository.findAll();
        if (deployments.isEmpty()) {
            return;
        }
        Map<UUID, LabourDesignation> designationsById = labourDesignationRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(LabourDesignation::getId, d -> d));

        LocalDate today = LocalDate.now();
        String sql = """
            INSERT INTO bipros_analytics.fact_labour_daily
            (project_id, labour_return_id, deployment_id, designation_id,
             skill_category, contractor_name, contractor_org_id, wbs_id, site_location,
             date, head_count, man_days, planned_head_count,
             daily_rate, daily_cost, source, event_ts, _version)
            VALUES (:projectId, NULL, :deploymentId, :designationId,
                    :skillCategory, :contractorName, NULL, NULL, '',
                    :date, 0, 0, :plannedHeadCount,
                    :dailyRate, :dailyCost, 'DEPLOYMENT_SNAPSHOT', now64(3), :version)
            """;
        for (ProjectLabourDeployment dep : deployments) {
            LabourDesignation d = designationsById.get(dep.getDesignationId());
            BigDecimal rate = dep.getActualDailyRate() != null
                    ? dep.getActualDailyRate()
                    : (d != null ? d.getDefaultDailyRate() : null);
            BigDecimal cost = (rate != null && dep.getWorkerCount() != null)
                    ? rate.multiply(BigDecimal.valueOf(dep.getWorkerCount()))
                    : null;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", dep.getProjectId());
            params.put("deploymentId", dep.getId());
            params.put("designationId", dep.getDesignationId());
            params.put("skillCategory", d != null && d.getCategory() != null ? d.getCategory().name() : "");
            params.put("contractorName", d != null ? d.getDesignation() : "");
            params.put("date", today);
            params.put("plannedHeadCount", dep.getWorkerCount());
            params.put("dailyRate", rate);
            params.put("dailyCost", cost);
            params.put("version", VERSION);
            clickHouse.execute(sql, params);
        }
        log.debug("Synced {} labour deployment snapshot rows", deployments.size());
    }
}
