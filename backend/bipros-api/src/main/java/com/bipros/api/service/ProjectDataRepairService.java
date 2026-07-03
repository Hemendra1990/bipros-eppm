package com.bipros.api.service;

import com.bipros.activity.application.percent.PercentCompleteCalculator;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityEditStatus;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.model.ActivitySupervisor;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.activity.domain.repository.ActivitySupervisorRepository;
import com.bipros.api.dto.ActivityStatusCorrectionRequest;
import com.bipros.api.dto.ActivityStatusCorrectionResponse;
import com.bipros.api.dto.DataHealthResponse;
import com.bipros.api.dto.EpsCodeCorrectionResponse;
import com.bipros.api.dto.RepairReport;
import com.bipros.api.dto.RepairRequest;
import com.bipros.common.util.AuditService;
import com.bipros.dbs.service.DbsAggregationService;
import com.bipros.project.application.service.BoqRebuildService;
import com.bipros.project.application.service.DailyProgressReportService;
import com.bipros.project.application.service.DprRescaleCalculator;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprEquipment;
import com.bipros.project.domain.model.DprManpower;
import com.bipros.project.domain.model.ManpowerCategory;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.resource.application.service.role.RoleProductivityNormResolver;
import com.bipros.resource.application.service.role.RoleRateResolver;
import com.bipros.resource.domain.model.ProductivityNormType;
import com.bipros.resource.domain.model.WorkActivity;
import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;
import com.bipros.resource.domain.model.GradeMaster;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.GradeMasterRepository;
import com.bipros.resource.domain.repository.ManpowerCategoryMasterRepository;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.WorkActivityRepository;
import com.bipros.resource.domain.repository.role.EquipmentRoleVariantRepository;
import com.bipros.resource.domain.repository.role.ManpowerRoleRateRepository;
import com.bipros.resource.domain.repository.role.MaterialRoleVariantRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDataRepairService {

  private final DailyProgressReportRepository dprRepo;
  private final ActivityRepository activityRepo;
  private final ActivitySupervisorRepository activitySupervisorRepo;
  private final DprManpowerRepository dprManpowerRepo;
  private final ManpowerRoleRateRepository manpowerRoleRateRepo;
  private final EquipmentRoleVariantRepository equipmentRoleVariantRepo;
  private final MaterialRoleVariantRepository materialRoleVariantRepo;
  private final ManpowerCategoryMasterRepository manpowerCategoryMasterRepo;
  private final GradeMasterRepository gradeMasterRepo;
  private final WorkActivityRepository workActivityRepo;
  private final DprEquipmentRepository dprEquipmentRepo;
  private final RoleProductivityNormResolver normResolver;
  private final RoleRateResolver rateResolver;
  private final ResourceAssignmentRepository resourceAssignmentRepo;
  // Phase B rebuild dependencies
  private final BoqRebuildService boqRebuildService;
  private final DailyProgressReportService dprService;
  private final DbsAggregationService dbsAggregationService;
  private final ActivitySubContractorAssignmentRepository scAssignmentRepo;
  private final PercentCompleteCalculator percentCompleteCalculator;
  private final AuditService auditService;

  // Self-proxy: lets repair(...) route Phase-A calls through the Spring proxy so each runs in
  // (and commits) its own transaction. NOT in the Lombok constructor — non-final, package-private
  // so the pure-Mockito unit test can wire it (service.self = service) without a Spring context.
  @org.springframework.beans.factory.annotation.Autowired
  @org.springframework.context.annotation.Lazy
  ProjectDataRepairService self;

  @PersistenceContext
  private EntityManager em;

  /**
   * Data-correction (admin only): directly overwrite an EPS node's {@code code}. The code is
   * immutable through the normal EPS update API, so this repair path issues the UPDATE directly.
   * Enforces the same rules as create — non-blank, ≤20 chars, unique.
   */
  @Transactional
  public EpsCodeCorrectionResponse correctEpsCode(UUID epsNodeId, String newCode) {
    if (epsNodeId == null) {
      throw new BusinessRuleException("EPS_NODE_ID_REQUIRED", "epsNodeId is required");
    }
    if (newCode == null || newCode.isBlank()) {
      throw new BusinessRuleException("EPS_CODE_REQUIRED", "code is required");
    }
    String code = newCode.trim();
    if (code.length() > 20) {
      throw new BusinessRuleException("EPS_CODE_TOO_LONG", "code must not exceed 20 characters");
    }

    List<Object[]> existing = em.createNativeQuery(
            "SELECT code, name FROM project.eps_nodes WHERE id = :id")
        .setParameter("id", epsNodeId)
        .getResultList();
    if (existing.isEmpty()) {
      throw new ResourceNotFoundException("EpsNode", epsNodeId);
    }
    String oldCode = (String) existing.get(0)[0];
    String name = (String) existing.get(0)[1];

    Number dupes = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM project.eps_nodes WHERE code = :code AND id <> :id")
        .setParameter("code", code)
        .setParameter("id", epsNodeId)
        .getSingleResult();
    if (dupes.longValue() > 0) {
      throw new BusinessRuleException("EPS_CODE_DUPLICATE",
          "EPS node with code '" + code + "' already exists");
    }

    int updated = em.createNativeQuery(
            "UPDATE project.eps_nodes SET code = :code WHERE id = :id")
        .setParameter("code", code)
        .setParameter("id", epsNodeId)
        .executeUpdate();

    log.info("EPS code correction: node={} '{}' -> '{}' ({} row updated)", epsNodeId, oldCode, code, updated);
    return new EpsCodeCorrectionResponse(epsNodeId, name, oldCode, code, updated);
  }

  @Transactional(readOnly = true)
  public DataHealthResponse diagnose(UUID projectId) {
    List<DailyProgressReport> dprs = dprRepo.findByProjectId(projectId);
    int resourceLess = 0;
    int supervisorIssues = 0;
    for (DailyProgressReport d : dprs) {
      // resource-less + System/blank supervisor are the headline health signals; deeper
      // per-row scans (null category, unit mismatch) are added with their repair phases.
      if (d.getSupervisorName() == null || d.getSupervisorName().isBlank()
          || d.getSupervisorName().startsWith("System") || d.getSupervisorName().contains("—")) {
        supervisorIssues++;
      }
    }
    return new DataHealthResponse(
        projectId, dprs.size(), supervisorIssues, 0, 0, resourceLess, 0, 0,
        dprRepo.findMinReportDate(projectId).orElse(null),
        dprRepo.findMaxReportDate(projectId).orElse(null));
  }

  @Transactional
  public int repairSupervisors(UUID projectId, boolean dryRun) {
    Map<UUID, Activity> actById = activityRepo.findByProjectId(projectId).stream()
        .collect(Collectors.toMap(Activity::getId, a -> a, (a, b) -> a));
    // Pre-load all supervisor rows for the project's activities once (avoids an N+1 round-trip
    // per DPR — the per-activity findByActivityId calls below were O(#DPRs)).
    Map<UUID, List<ActivitySupervisor>> supervisorsByActivity =
        activitySupervisorRepo.findByActivityIdIn(actById.keySet()).stream()
            .collect(Collectors.groupingBy(ActivitySupervisor::getActivityId));
    int changed = 0;
    for (DailyProgressReport d : dprRepo.findByProjectId(projectId)) {
      Activity act = d.getActivityId() == null ? null : actById.get(d.getActivityId());
      if (act == null || act.getSupervisorUserId() == null) continue;
      List<ActivitySupervisor> actSupervisors =
          supervisorsByActivity.getOrDefault(act.getId(), List.of());
      Set<UUID> valid = actSupervisors.stream()
          .map(ActivitySupervisor::getUserId).collect(Collectors.toSet());
      boolean broken = d.getSupervisorUserId() == null
          || (!valid.isEmpty() && !valid.contains(d.getSupervisorUserId()))
          || d.getSupervisorName() == null || d.getSupervisorName().isBlank()
          || d.getSupervisorName().startsWith("System") || d.getSupervisorName().contains("—");
      if (!broken) continue;
      UUID primary = act.getSupervisorUserId();
      String name = actSupervisors.stream()
          .filter(s -> primary.equals(s.getUserId()))
          .map(ActivitySupervisor::getUserNameSnapshot).findFirst().orElse(d.getSupervisorName());
      if (!dryRun) {
        d.setSupervisorUserId(primary);
        d.setSupervisorName(name);
        dprRepo.save(d);
      }
      changed++;
    }
    log.info("[ProjectDataRepairService] supervisors: {} DPRs {} (dryRun={})",
        changed, dryRun ? "would change" : "changed", dryRun);
    return changed;
  }

  /**
   * Phase A2: backfill null {@code DprManpower.category} to {@code SKILLED},
   * and backfill null {@code categoryId}/{@code gradeId} on manpower rate variants
   * (scoped to variants referenced by this project's DPR rows).
   * Equipment make/model and material specGrade cannot be null at DB level so those
   * variant repairs are effectively no-ops on current data but are wired defensively.
   *
   * @return count of rows changed (or would-change when dryRun)
   */
  @Transactional
  public int repairRateLabels(UUID projectId, boolean dryRun) {
    UUID skilledId = manpowerCategoryMasterRepo.findByName("Skilled")
        .map(ManpowerCategoryMaster::getId).orElse(null);
    UUID gradeAId = gradeMasterRepo.findByCode("A")
        .map(GradeMaster::getId).orElse(null);

    int changed = 0;
    List<DailyProgressReport> dprs = dprRepo.findByProjectId(projectId);

    // (a) Backfill null DprManpower.category enum; also collect manpowerRoleRateIds for (b)
    Set<UUID> referencedRateIds = new java.util.LinkedHashSet<>();
    for (DailyProgressReport d : dprs) {
      List<DprManpower> rows = dprManpowerRepo.findByDprId(d.getId());
      for (DprManpower m : rows) {
        if (m.getManpowerRoleRateId() != null) {
          referencedRateIds.add(m.getManpowerRoleRateId());
        }
        if (m.getCategory() == null) {
          if (!dryRun) {
            m.setCategory(ManpowerCategory.SKILLED);
            dprManpowerRepo.save(m);
          }
          changed++;
        }
      }
    }

    // (b) Backfill null categoryId/gradeId on manpower rate variants referenced by this project
    if (skilledId != null && gradeAId != null && !referencedRateIds.isEmpty()) {
      for (com.bipros.resource.domain.model.role.ManpowerRoleRate v
          : manpowerRoleRateRepo.findAllById(referencedRateIds)) {
        boolean needsFix = v.getCategoryId() == null || v.getGradeId() == null;
        if (needsFix) {
          if (!dryRun) {
            if (v.getCategoryId() == null) v.setCategoryId(skilledId);
            if (v.getGradeId() == null) v.setGradeId(gradeAId);
            manpowerRoleRateRepo.save(v);
          }
          changed++;
        }
      }
    }

    log.info("[ProjectDataRepairService] rate labels: {} rows {} (dryRun={})",
        changed, dryRun ? "would change" : "changed", dryRun);
    return changed;
  }

  /**
   * Phase A3: align each DPR's {@code unit} to its activity's work-activity {@code defaultUnit}.
   * Comparison is case-insensitive and trim-safe. Skips DPRs with no linked activity or
   * where the activity has no linked work-activity or where the work-activity has no defaultUnit.
   *
   * @return count of DPRs whose unit was changed (or would-change when dryRun)
   */
  @Transactional
  public int repairUnits(UUID projectId, boolean dryRun) {
    Map<UUID, Activity> actById = activityRepo.findByProjectId(projectId).stream()
        .collect(Collectors.toMap(Activity::getId, a -> a, (a, b) -> a));

    // Pre-fetch work-activity default units for all distinct workActivityIds in this project
    Map<UUID, String> defaultUnitByWa = actById.values().stream()
        .map(Activity::getWorkActivityId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .collect(Collectors.toMap(
            waId -> waId,
            waId -> workActivityRepo.findById(waId)
                .map(WorkActivity::getDefaultUnit)
                .orElse(null),
            (a, b) -> a));

    int changed = 0;
    for (DailyProgressReport d : dprRepo.findByProjectId(projectId)) {
      if (d.getActivityId() == null) continue;
      Activity act = actById.get(d.getActivityId());
      if (act == null || act.getWorkActivityId() == null) continue;
      String defaultUnit = defaultUnitByWa.get(act.getWorkActivityId());
      if (defaultUnit == null || defaultUnit.isBlank()) continue;

      String dprUnit = d.getUnit() == null ? "" : d.getUnit().trim();
      if (dprUnit.equalsIgnoreCase(defaultUnit.trim())) continue;

      if (!dryRun) {
        d.setUnit(defaultUnit);
        dprRepo.save(d);
      }
      changed++;
    }

    log.info("[ProjectDataRepairService] units: {} DPRs {} (dryRun={})",
        changed, dryRun ? "would change" : "changed", dryRun);
    return changed;
  }

  /**
   * Phase A4: rescale each DPR's manpower + equipment quantities to be NORM-PROPORTIONATE so
   * capacity-utilization efficiency {@code ((qty/norm)/nos)} lands in the healthy band
   * {@code [0.85, 1.05]}, and so derived line costs become realistic. For every DPR with
   * {@code qtyExecuted > 0}, each side is independently scaled to its own norm
   * (manpower → {@code outputPerManPerDay} with {@code outputPerDay} fallback;
   * equipment → {@code outputPerDay}). When a side has no rows but the activity has assignments
   * of that type, one row is synthesized per assignment (shell fill) before distributing.
   * The per-side {@code targetNos} is independent of {@code WorkActivity.normCombination}
   * (SERIES/PARALLEL/SUBSTITUTE only affect the DPR preview, not this rescale).
   *
   * @return count of DPRs whose resource rows were rescaled or filled
   *         (or would-be-rescaled when dryRun)
   */
  @Transactional
  public int repairRescale(UUID projectId, boolean dryRun) {
    Map<UUID, Activity> actById = activityRepo.findByProjectId(projectId).stream()
        .collect(Collectors.toMap(Activity::getId, a -> a, (a, b) -> a));

    int changed = 0;
    for (DailyProgressReport dpr : dprRepo.findByProjectId(projectId)) {
      BigDecimal qty = dpr.getQtyExecuted();
      if (qty == null || qty.signum() <= 0) continue;
      if (dpr.getActivityId() == null) continue;
      Activity act = actById.get(dpr.getActivityId());
      if (act == null || act.getWorkActivityId() == null) continue;
      UUID workActivityId = act.getWorkActivityId();

      boolean touched = false;
      touched |= rescaleManpower(projectId, dpr, act, workActivityId, qty, dryRun);
      touched |= rescaleEquipment(projectId, dpr, act, workActivityId, qty, dryRun);
      if (touched) changed++;
    }

    log.info("[ProjectDataRepairService] rescale: {} DPRs {} (dryRun={})",
        changed, dryRun ? "would change" : "changed", dryRun);
    return changed;
  }

  private boolean rescaleManpower(UUID projectId, DailyProgressReport dpr, Activity act,
                                  UUID workActivityId, BigDecimal qty, boolean dryRun) {
    List<DprManpower> rows = new ArrayList<>(dprManpowerRepo.findByDprId(dpr.getId()));
    if (rows.isEmpty()) {
      for (ResourceAssignment ra : resourceAssignmentRepo.findByActivityId(act.getId())) {
        if (ra.getManpowerRoleRateId() == null) continue;
        rows.add(DprManpower.builder()
            .dprId(dpr.getId())
            .trade("Crew")
            .nos(1)
            .category(com.bipros.project.domain.model.ManpowerCategory.SKILLED)
            .roleId(ra.getRoleId())
            .manpowerRoleRateId(ra.getManpowerRoleRateId())
            .build());
      }
      if (rows.isEmpty()) return false;
    }

    // Per-side norm = the norm for the first row's role (each side scaled to its own norm).
    UUID normRoleId = rows.get(0).getRoleId();
    BigDecimal norm = normResolver.resolveByRole(
            workActivityId, normRoleId, null, null, null, null, ProductivityNormType.MANPOWER)
        .map(n -> n.getOutputPerManPerDay() != null ? n.getOutputPerManPerDay() : n.getOutputPerDay())
        .orElse(null);
    if (norm == null) return false;

    int target = DprRescaleCalculator.targetNos(qty, norm, dpr.getId());
    if (target <= 0) return false;
    List<Integer> split = DprRescaleCalculator.distribute(target,
        rows.stream().map(r -> r.getNos() == null ? 0 : r.getNos()).toList());

    if (!dryRun) {
      for (int i = 0; i < rows.size(); i++) {
        DprManpower r = rows.get(i);
        int nos = split.get(i);
        r.setNos(nos);
        BigDecimal rate = rateResolver.resolveRate(projectId, "MANPOWER", r.getManpowerRoleRateId());
        r.setUnitRate(rate);
        r.setLineCost(rate == null ? null : rate.multiply(BigDecimal.valueOf(nos)));
        dprManpowerRepo.save(r);
      }
    }
    return true;
  }

  private boolean rescaleEquipment(UUID projectId, DailyProgressReport dpr, Activity act,
                                   UUID workActivityId, BigDecimal qty, boolean dryRun) {
    List<DprEquipment> rows = new ArrayList<>(dprEquipmentRepo.findByDprId(dpr.getId()));
    if (rows.isEmpty()) {
      for (ResourceAssignment ra : resourceAssignmentRepo.findByActivityId(act.getId())) {
        if (ra.getEquipmentRoleVariantId() == null) continue;
        rows.add(DprEquipment.builder()
            .dprId(dpr.getId())
            .equipmentType("Equipment")
            .nos(1)
            .roleId(ra.getRoleId())
            .equipmentRoleVariantId(ra.getEquipmentRoleVariantId())
            .build());
      }
      if (rows.isEmpty()) return false;
    }

    UUID normRoleId = rows.get(0).getRoleId();
    BigDecimal norm = normResolver.resolveByRole(
            workActivityId, normRoleId, null, null, null, null, ProductivityNormType.EQUIPMENT)
        .map(n -> n.getOutputPerDay() != null ? n.getOutputPerDay() : n.getOutputPerManPerDay())
        .orElse(null);
    if (norm == null) return false;

    int target = DprRescaleCalculator.targetNos(qty, norm, dpr.getId());
    if (target <= 0) return false;
    List<Integer> split = DprRescaleCalculator.distribute(target,
        rows.stream().map(r -> r.getNos() == null ? 0 : r.getNos()).toList());

    if (!dryRun) {
      for (int i = 0; i < rows.size(); i++) {
        DprEquipment r = rows.get(i);
        int nos = split.get(i);
        r.setNos(nos);
        BigDecimal rate = rateResolver.resolveRate(projectId, "EQUIPMENT", r.getEquipmentRoleVariantId());
        r.setUnitRate(rate);
        r.setLineCost(rate == null ? null : rate.multiply(BigDecimal.valueOf(nos)));
        dprEquipmentRepo.save(r);
      }
    }
    return true;
  }

  /**
   * Full orchestration: diagnose (before) → Phase-A repairs → Phase-B aggregate rebuild
   * (only when not dryRun) → diagnose (after). Returns a RepairReport with per-phase counts.
   *
   * <p>Phases default to all 5 (SUPERVISORS, RATE_LABELS, UNITS, RESCALE, REBUILD) when
   * {@code request.getPhases()} is null or empty; otherwise the provided subset is used.
   * Phase-A repair methods run with the dryRun flag. The REBUILD phase runs the from-scratch
   * rebuilds ONLY when {@code !dryRun}; when dryRun, REBUILD → 0 and no rebuild deps are called.
   * EVM refresh is intentionally omitted — Costs/Insights compute on read, so no persisted EVM
   * refresh is needed; this avoids coupling to CalculateEvmRequest's exact shape.
   *
   * <p>Deliberately NOT {@code @Transactional} — Phase-A repairs must COMMIT (via the self proxy)
   * before the Phase-B rebuild runs, because {@code DbsAggregationService} is {@code REQUIRES_NEW}
   * and cannot see uncommitted writes.
   */
  public RepairReport repair(UUID projectId, RepairRequest request) {
    boolean dry = request.isDryRun();
    Set<String> phases = (request.getPhases() == null || request.getPhases().isEmpty())
        ? Set.of("SUPERVISORS", "RATE_LABELS", "UNITS", "RESCALE", "REBUILD")
        : new HashSet<>(request.getPhases());

    DataHealthResponse before = diagnose(projectId);
    Map<String, Integer> changed = new LinkedHashMap<>();

    if (phases.contains("SUPERVISORS")) changed.put("SUPERVISORS", self.repairSupervisors(projectId, dry));
    if (phases.contains("RATE_LABELS")) changed.put("RATE_LABELS", self.repairRateLabels(projectId, dry));
    if (phases.contains("UNITS"))       changed.put("UNITS", self.repairUnits(projectId, dry));
    if (phases.contains("RESCALE"))     changed.put("RESCALE", self.repairRescale(projectId, dry));

    if (phases.contains("REBUILD") && !dry) {
      int boq = boqRebuildService.rebuildFromDprs(projectId);

      // Per-activity resource-plan rollup
      for (Activity a : activityRepo.findByProjectId(projectId)) {
        dprService.recomputeActivityResourceActuals(a.getId());
      }

      // SC actuals: collect all SC assignment ids for this project and recompute
      Set<UUID> scIds = scAssignmentRepo.findByProjectId(projectId).stream()
          .map(com.bipros.resource.domain.model.ActivitySubContractorAssignment::getId)
          .collect(Collectors.toSet());
      if (!scIds.isEmpty()) {
        dprService.recomputeScActualsForAssignments(scIds);
      }

      // DBS range recompute
      LocalDate from = dprRepo.findMinReportDate(projectId).orElse(null);
      LocalDate to = dprRepo.findMaxReportDate(projectId).orElse(null);
      if (from != null && to != null) {
        dbsAggregationService.recomputeRange(projectId, from, to);
      }

      changed.put("REBUILD", boq);
    } else if (phases.contains("REBUILD")) {
      changed.put("REBUILD", 0); // dry-run: nothing rebuilt
    }

    DataHealthResponse after = dry ? before : diagnose(projectId);
    log.info("[ProjectDataRepairService] repair project={} dryRun={} changed={}", projectId, dry, changed);
    return new RepairReport(dry, new ArrayList<>(phases), changed, before, after);
  }

  /**
   * Admin data-correction: re-derive each activity's status/percentComplete from its own
   * APPROVED DPR/BOQ data, using the same {@link PercentCompleteCalculator} engine the app
   * already uses (so the recomputed value is stable and consistent with normal DPR-approval
   * flow). Precedence per activity: no approved DPR at all → reset to NOT_STARTED/0; else if
   * the activity has linked BOQ workdone → BOQ-driven percent (kept as-is if already 100%);
   * else fall back to the activity's own {@code percentCompleteType} (DURATION/UNITS/PHYSICAL).
   *
   * <p>{@code dryRun} (default true) computes and reports without persisting. When dryRun,
   * NO setter is called on the loaded {@link Activity} — JPA dirty-checking would otherwise
   * flush any mutated field at commit even without an explicit {@code save()}, corrupting data
   * under a "preview" call. All intended new values are computed into locals first and only
   * applied to the entity inside the {@code !dryRun && changed} branch.
   */
  @Transactional
  public ActivityStatusCorrectionResponse correctActivityStatus(
      UUID projectId, ActivityStatusCorrectionRequest req) {
    if (req == null || req.getActivityIds() == null || req.getActivityIds().isEmpty()) {
      throw new BusinessRuleException("ACTIVITY_IDS_REQUIRED", "activityIds is required");
    }
    boolean dryRun = req.isDryRun();
    LocalDate today = LocalDate.now();

    int resetNotStarted = 0;
    int resetInProgress = 0;
    int keptCompleted = 0;
    int noBoqRecomputed = 0;
    int skipped = 0;
    List<ActivityStatusCorrectionResponse.Result> results = new ArrayList<>();

    for (UUID id : req.getActivityIds()) {
      Activity a = activityRepo.findById(id).orElse(null);
      if (a == null) {
        results.add(new ActivityStatusCorrectionResponse.Result(
            id, null, null, null, null, null, null, "SKIPPED_NOT_FOUND", null));
        skipped++;
        continue;
      }
      if (!projectId.equals(a.getProjectId())) {
        String statusName = a.getStatus() == null ? null : a.getStatus().name();
        results.add(new ActivityStatusCorrectionResponse.Result(
            id, a.getCode(), a.getName(), statusName, a.getPercentComplete(),
            statusName, a.getPercentComplete(), "SKIPPED_WRONG_PROJECT", null));
        skipped++;
        continue;
      }

      ActivityStatus oldStatusEnum = a.getStatus();
      String oldStatus = oldStatusEnum == null ? null : oldStatusEnum.name();
      Double oldPct = a.getPercentComplete();
      String note = (a.getEditStatus() == ActivityEditStatus.LOCKED) ? "was LOCKED" : null;

      Optional<LocalDate> earliestApproved = dprRepo.findEarliestApprovedReportDateForActivity(id);
      boolean hasApprovedDpr = earliestApproved.isPresent();
      BigDecimal bq = dprRepo.sumLinkedBoqQtyApproved(id);
      double boqQty = bq == null ? 0.0 : bq.doubleValue();
      BigDecimal wd = dprRepo.sumActivityWorkdoneOnBoqApproved(id);
      Double workdone = wd == null ? 0.0 : wd.doubleValue();

      // Intended new values, computed into locals only — no setter is called on `a` here.
      ActivityStatus newStatusEnum = oldStatusEnum;
      Double newPct = oldPct;
      LocalDate newStart = a.getActualStartDate();
      LocalDate newFinish = a.getActualFinishDate();
      String outcome;

      if (!hasApprovedDpr) {
        newStatusEnum = ActivityStatus.NOT_STARTED;
        newPct = 0.0;
        newStart = null;
        newFinish = null;
        outcome = "RESET_NOT_STARTED";
      } else if (boqQty > 0) {
        PercentCompleteCalculator.Result r =
            percentCompleteCalculator.calculateBoq(a, workdone, boqQty, today);
        double pct = r.percent();
        if (pct >= 100.0) {
          outcome = "KEPT_COMPLETED"; // new == old, no change
        } else {
          newPct = pct;
          newStatusEnum = r.status();
          newFinish = null; // clear the wrongful completion
          if (newStatusEnum == ActivityStatus.IN_PROGRESS && a.getActualStartDate() == null) {
            newStart = earliestApproved.orElse(null);
          } else if (newStatusEnum == ActivityStatus.NOT_STARTED) {
            // pct==0 => derive() only returns NOT_STARTED when actualStart was already null; kept for clarity
            newStart = null;
          } else {
            newStart = a.getActualStartDate();
          }
          outcome = (newStatusEnum == ActivityStatus.IN_PROGRESS)
              ? "RESET_IN_PROGRESS" : "RESET_NOT_STARTED";
        }
      } else {
        PercentCompleteCalculator.Result r = percentCompleteCalculator.calculate(a, null, null, today);
        if (r.isKeepPrior()) {
          outcome = "SKIPPED_NO_BOQ_NO_RECOMPUTE"; // new == old, no change
        } else {
          newPct = r.percent();
          newStatusEnum = r.status();
          newFinish = r.forcedActualFinish() != null
              ? r.forcedActualFinish()
              : (newPct != null && newPct < 100.0 ? null : a.getActualFinishDate());
          newStart = a.getActualStartDate();
          outcome = "RESET_FROM_TYPE";
        }
      }

      boolean changed = outcome.startsWith("RESET_");
      if (!dryRun && changed) {
        a.setPercentComplete(newPct);
        a.setStatus(newStatusEnum);
        a.setActualStartDate(newStart);
        a.setActualFinishDate(newFinish);
        activityRepo.save(a);
        if (!Objects.equals(oldPct, newPct)) {
          auditService.logUpdate("Activity", id, "percentComplete", oldPct, newPct);
        }
        if (oldStatusEnum != newStatusEnum) {
          auditService.logUpdate("Activity", id, "status", oldStatusEnum, newStatusEnum);
        }
      }

      String newStatusName = newStatusEnum == null ? oldStatus : newStatusEnum.name();
      results.add(new ActivityStatusCorrectionResponse.Result(
          id, a.getCode(), a.getName(), oldStatus, oldPct, newStatusName, newPct, outcome, note));

      switch (outcome) {
        case "RESET_NOT_STARTED" -> resetNotStarted++;
        case "RESET_IN_PROGRESS" -> resetInProgress++;
        case "KEPT_COMPLETED" -> keptCompleted++;
        case "RESET_FROM_TYPE" -> noBoqRecomputed++;
        default -> skipped++; // SKIPPED_NO_BOQ_NO_RECOMPUTE
      }
    }

    log.info("[ProjectDataRepairService] activity status correction: project={} dryRun={} "
            + "resetNotStarted={} resetInProgress={} keptCompleted={} noBoqRecomputed={} skipped={}",
        projectId, dryRun, resetNotStarted, resetInProgress, keptCompleted, noBoqRecomputed, skipped);

    return new ActivityStatusCorrectionResponse(
        dryRun,
        new ActivityStatusCorrectionResponse.Summary(
            resetNotStarted, resetInProgress, keptCompleted, noBoqRecomputed, skipped),
        results);
  }
}
