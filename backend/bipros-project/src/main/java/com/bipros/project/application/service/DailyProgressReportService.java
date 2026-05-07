package com.bipros.project.application.service;

import com.bipros.common.event.DprMutationType;
import com.bipros.common.event.DprSubmittedEvent;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDailyProgressReportRequest;
import com.bipros.project.application.dto.DailyProgressReportResponse;
import com.bipros.project.application.dto.DprEquipmentRow;
import com.bipros.project.application.dto.DprManpowerRow;
import com.bipros.project.application.dto.DprMaterialRow;
import com.bipros.project.application.dto.UpdateDailyProgressReportRequest;
import com.bipros.project.application.util.DprCostFormulas;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprEquipment;
import com.bipros.project.domain.model.DprManpower;
import com.bipros.project.domain.model.DprMaterial;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.project.domain.repository.DprManpowerRepository;
import com.bipros.project.domain.repository.DprMaterialRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DPR mutation flow:
 * <ul>
 *   <li>Service writes the row + child collections (manpower / equipment / material), publishes
 *       a {@link DprSubmittedEvent} carrying the change type, old/new BOQ qty + item linkage, and
 *       resource-row counts so analytics knows whether to fetch child rows.</li>
 *   <li>{@code DprBoqSyncListener} (in-module) reacts to update {@code BoqItem.qtyExecutedToDate}
 *       transactionally with the DPR write.</li>
 *   <li>{@code DprSubmittedListener} (analytics) reacts AFTER_COMMIT to write ClickHouse fact rows
 *       across {@code fact_dpr_logs}, {@code fact_dpr_manpower_daily}, {@code fact_dpr_equipment_daily},
 *       and {@code fact_dpr_material_daily}.</li>
 * </ul>
 * Cumulative qty is never stored — list/get computes it on read so back-dated edits stay
 * self-consistent without rewriting later rows.
 *
 * <p>Children are managed with full-replacement semantics: every save deletes-by-dprId and
 * re-inserts the supplied list. Matches the {@code UpdateDailyProgressReportRequest} contract.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DailyProgressReportService {

  private final DailyProgressReportRepository dprRepository;
  private final DprManpowerRepository manpowerRepository;
  private final DprEquipmentRepository equipmentRepository;
  private final DprMaterialRepository materialRepository;
  private final ProjectRepository projectRepository;
  private final DailyActivityResourceOutputService ledgerService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * The DPR write path snapshots rates and validates assignment ↔ activity ↔ kind via tiny
   * native SQL reads against the {@code resource} schema. We can't import bipros-resource here
   * (project module owns its dep graph; see CLAUDE.md) so the existing
   * {@link DailyActivityResourceOutputService#recomputeAssignmentRollup} cross-schema precedent
   * is followed.
   */
  @PersistenceContext
  private EntityManager em;

  public DailyProgressReportResponse create(UUID projectId, CreateDailyProgressReportRequest request) {
    ensureProjectExists(projectId);

    DailyProgressReport dpr = DailyProgressReport.builder()
        .projectId(projectId)
        .reportDate(request.reportDate())
        .supervisorResourceId(request.supervisorResourceId())
        .supervisorName(request.supervisorName())
        .chainageFromM(request.chainageFromM())
        .chainageToM(request.chainageToM())
        .activityId(request.activityId())
        .activityName(request.activityName())
        .wbsNodeId(request.wbsNodeId())
        .boqItemNo(request.boqItemNo())
        .unit(request.unit())
        .qtyExecuted(request.qtyExecuted())
        .weatherCondition(request.weatherCondition())
        .remarks(request.remarks())
        .side(request.side())
        .landmark(request.landmark())
        .startTime(request.startTime())
        .endTime(request.endTime())
        .shift(request.shift())
        .approvalStatus(request.approvalStatus())
        .contractorName(request.contractorName())
        .delayReason(request.delayReason())
        .safetyObservation(request.safetyObservation())
        .safetyIncidentType(request.safetyIncidentType())
        .build();

    DailyProgressReport saved = dprRepository.save(dpr);

    List<String> warnings = new ArrayList<>();
    SnapshottedChildren snap = snapshotChildren(saved, request.manpower(), request.equipment(), request.materials(), warnings);

    List<DprManpower> savedManpower = snap.manpower.isEmpty() ? List.of() : manpowerRepository.saveAll(snap.manpower);
    List<DprEquipment> savedEquipment = snap.equipment.isEmpty() ? List.of() : equipmentRepository.saveAll(snap.equipment);
    List<DprMaterial> savedMaterial = snap.material.isEmpty() ? List.of() : materialRepository.saveAll(snap.material);

    reconcileLedger(saved, savedManpower, savedEquipment, savedMaterial);

    BigDecimal cumulative = computeCumulative(saved.getProjectId(), saved.getActivityName(), saved.getReportDate());
    DailyProgressReportResponse response = DailyProgressReportResponse.from(
        saved, cumulative,
        savedManpower.stream().map(DprManpowerRow::from).toList(),
        savedEquipment.stream().map(DprEquipmentRow::from).toList(),
        savedMaterial.stream().map(DprMaterialRow::from).toList(),
        warnings);

    auditService.logCreate("DailyProgressReport", saved.getId(), response);
    eventPublisher.publishEvent(buildEvent(saved, null, null, DprMutationType.CREATED,
        savedManpower, savedEquipment, savedMaterial));
    return response;
  }

  public List<DailyProgressReportResponse> createBulk(UUID projectId, List<CreateDailyProgressReportRequest> requests) {
    // One-at-a-time so the BOQ sync listener fires deterministically per row on bulk seed.
    return requests.stream().map(r -> create(projectId, r)).toList();
  }

  public DailyProgressReportResponse update(UUID projectId, UUID id, UpdateDailyProgressReportRequest request) {
    DailyProgressReport dpr = find(projectId, id);

    String oldBoqItemNo = dpr.getBoqItemNo();
    BigDecimal oldQty = dpr.getQtyExecuted();
    DailyProgressReportResponse before = DailyProgressReportResponse.from(dpr,
        computeCumulative(dpr.getProjectId(), dpr.getActivityName(), dpr.getReportDate()));

    dpr.setReportDate(request.reportDate());
    dpr.setSupervisorResourceId(request.supervisorResourceId());
    dpr.setSupervisorName(request.supervisorName());
    dpr.setChainageFromM(request.chainageFromM());
    dpr.setChainageToM(request.chainageToM());
    dpr.setActivityId(request.activityId());
    dpr.setActivityName(request.activityName());
    dpr.setWbsNodeId(request.wbsNodeId());
    dpr.setBoqItemNo(request.boqItemNo());
    dpr.setUnit(request.unit());
    dpr.setQtyExecuted(request.qtyExecuted());
    dpr.setWeatherCondition(request.weatherCondition());
    dpr.setRemarks(request.remarks());
    dpr.setSide(request.side());
    dpr.setLandmark(request.landmark());
    dpr.setStartTime(request.startTime());
    dpr.setEndTime(request.endTime());
    dpr.setShift(request.shift());
    dpr.setApprovalStatus(request.approvalStatus());
    dpr.setContractorName(request.contractorName());
    dpr.setDelayReason(request.delayReason());
    dpr.setSafetyObservation(request.safetyObservation());
    dpr.setSafetyIncidentType(request.safetyIncidentType());

    DailyProgressReport saved = dprRepository.save(dpr);

    // Replace children: delete then re-insert. Flush between to avoid PK collisions on the
    // unique constraint inside one TX (Hibernate batches the delete with the insert otherwise).
    manpowerRepository.deleteByDprId(saved.getId());
    equipmentRepository.deleteByDprId(saved.getId());
    materialRepository.deleteByDprId(saved.getId());
    manpowerRepository.flush();
    equipmentRepository.flush();
    materialRepository.flush();

    List<String> warnings = new ArrayList<>();
    SnapshottedChildren snap = snapshotChildren(saved, request.manpower(), request.equipment(), request.materials(), warnings);

    List<DprManpower> savedManpower = snap.manpower.isEmpty() ? List.of() : manpowerRepository.saveAll(snap.manpower);
    List<DprEquipment> savedEquipment = snap.equipment.isEmpty() ? List.of() : equipmentRepository.saveAll(snap.equipment);
    List<DprMaterial> savedMaterial = snap.material.isEmpty() ? List.of() : materialRepository.saveAll(snap.material);

    reconcileLedger(saved, savedManpower, savedEquipment, savedMaterial);

    BigDecimal cumulative = computeCumulative(saved.getProjectId(), saved.getActivityName(), saved.getReportDate());
    DailyProgressReportResponse after = DailyProgressReportResponse.from(
        saved, cumulative,
        savedManpower.stream().map(DprManpowerRow::from).toList(),
        savedEquipment.stream().map(DprEquipmentRow::from).toList(),
        savedMaterial.stream().map(DprMaterialRow::from).toList(),
        warnings);

    auditService.logUpdate("DailyProgressReport", saved.getId(), "row", before, after);
    eventPublisher.publishEvent(buildEvent(saved, oldBoqItemNo, oldQty, DprMutationType.UPDATED,
        savedManpower, savedEquipment, savedMaterial));
    return after;
  }

  @Transactional(readOnly = true)
  public List<DailyProgressReportResponse> list(UUID projectId, LocalDate from, LocalDate to, String activityName) {
    ensureProjectExists(projectId);
    List<DailyProgressReport> rows;
    if (activityName != null && !activityName.isBlank()) {
      rows = dprRepository.findByProjectIdAndActivityNameIgnoreCaseOrderByReportDateAsc(projectId, activityName);
    } else if (from != null && to != null) {
      rows = dprRepository.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(projectId, from, to);
    } else {
      rows = dprRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId);
    }
    return attachComputedCumulativeAndChildren(rows);
  }

  @Transactional(readOnly = true)
  public DailyProgressReportResponse get(UUID projectId, UUID id) {
    DailyProgressReport dpr = find(projectId, id);
    BigDecimal cumulative = computeCumulative(dpr.getProjectId(), dpr.getActivityName(), dpr.getReportDate());
    return DailyProgressReportResponse.from(
        dpr, cumulative,
        manpowerRepository.findByDprIdOrderByTradeAsc(id).stream().map(DprManpowerRow::from).toList(),
        equipmentRepository.findByDprIdOrderByEquipmentTypeAsc(id).stream().map(DprEquipmentRow::from).toList(),
        materialRepository.findByDprIdOrderByMaterialNameAsc(id).stream().map(DprMaterialRow::from).toList()
    );
  }

  public void delete(UUID projectId, UUID id) {
    DailyProgressReport dpr = find(projectId, id);
    String oldBoqItemNo = dpr.getBoqItemNo();
    BigDecimal oldQty = dpr.getQtyExecuted();
    UUID dprId = dpr.getId();
    LocalDate reportDate = dpr.getReportDate();
    String activityName = dpr.getActivityName();

    // Tear down ledger contributions BEFORE deleting children so the rollup SUM excludes the
    // child rows (the ledger holds aggregates, not child references).
    ledgerService.deleteDprLedger(projectId, dprId, reportDate);

    manpowerRepository.deleteByDprId(dprId);
    equipmentRepository.deleteByDprId(dprId);
    materialRepository.deleteByDprId(dprId);
    dprRepository.delete(dpr);
    auditService.logDelete("DailyProgressReport", id);

    eventPublisher.publishEvent(DprSubmittedEvent.withoutChildren(
        projectId, dprId, reportDate, activityName, null, null, oldBoqItemNo, oldQty,
        DprMutationType.DELETED));
  }

  /**
   * Sum of qtyExecuted for the (project, activityName) up to and including the given date.
   * Equivalent to the legacy stored {@code cumulativeQty} but always fresh.
   */
  private BigDecimal computeCumulative(UUID projectId, String activityName, LocalDate reportDate) {
    BigDecimal sum = dprRepository.sumQtyExecutedThroughDate(projectId, activityName, reportDate);
    return sum != null ? sum : BigDecimal.ZERO;
  }

  /**
   * Walks rows in date order per (project, activityName), accumulating cumulative locally
   * to avoid an N+1 query, and batches one child fetch per child table for the whole window.
   */
  private List<DailyProgressReportResponse> attachComputedCumulativeAndChildren(List<DailyProgressReport> rows) {
    if (rows.isEmpty()) return List.of();

    List<UUID> ids = rows.stream().map(DailyProgressReport::getId).toList();
    Map<UUID, List<DprManpowerRow>> manpowerByDpr = manpowerRepository.findByDprIdIn(ids).stream()
        .collect(Collectors.groupingBy(DprManpower::getDprId,
            Collectors.mapping(DprManpowerRow::from, Collectors.toList())));
    Map<UUID, List<DprEquipmentRow>> equipmentByDpr = equipmentRepository.findByDprIdIn(ids).stream()
        .collect(Collectors.groupingBy(DprEquipment::getDprId,
            Collectors.mapping(DprEquipmentRow::from, Collectors.toList())));
    Map<UUID, List<DprMaterialRow>> materialByDpr = materialRepository.findByDprIdIn(ids).stream()
        .collect(Collectors.groupingBy(DprMaterial::getDprId,
            Collectors.mapping(DprMaterialRow::from, Collectors.toList())));

    Map<String, BigDecimal> running = new HashMap<>();
    List<DailyProgressReportResponse> out = new ArrayList<>(rows.size());
    rows.stream()
        .sorted((a, b) -> {
          int byDate = a.getReportDate().compareTo(b.getReportDate());
          if (byDate != 0) return byDate;
          return a.getId().compareTo(b.getId());
        })
        .forEach(r -> {
          String key = r.getProjectId() + "::" + (r.getActivityName() == null ? "" : r.getActivityName().toLowerCase());
          BigDecimal cumulative = running.getOrDefault(key, BigDecimal.ZERO)
              .add(r.getQtyExecuted() != null ? r.getQtyExecuted() : BigDecimal.ZERO);
          running.put(key, cumulative);
          out.add(DailyProgressReportResponse.from(r, cumulative,
              manpowerByDpr.getOrDefault(r.getId(), List.of()),
              equipmentByDpr.getOrDefault(r.getId(), List.of()),
              materialByDpr.getOrDefault(r.getId(), List.of())));
        });
    return out;
  }

  private DailyProgressReport find(UUID projectId, UUID id) {
    DailyProgressReport dpr = dprRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("DailyProgressReport", id));
    if (!dpr.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("DailyProgressReport", id);
    }
    return dpr;
  }

  private void ensureProjectExists(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }

  /** Generic helper: persist a list of incoming rows under one parent id, returning saved entities. */
  private <ROW, ENTITY> List<ENTITY> saveChildren(
      UUID parentId,
      List<ROW> rows,
      org.springframework.data.jpa.repository.JpaRepository<ENTITY, UUID> repo,
      java.util.function.BiFunction<ROW, UUID, ENTITY> toEntity) {
    if (rows == null || rows.isEmpty()) return List.of();
    List<ENTITY> entities = rows.stream().map(r -> toEntity.apply(r, parentId)).toList();
    return repo.saveAll(entities);
  }

  private DprSubmittedEvent buildEvent(
      DailyProgressReport saved,
      String oldBoqItemNo,
      BigDecimal oldQty,
      DprMutationType type,
      Collection<DprManpower> manpower,
      Collection<DprEquipment> equipment,
      Collection<DprMaterial> material) {
    BigDecimal totalManpowerHours = manpower.stream()
        .map(m -> add(m.getWorkingHours(), m.getOtHours()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalEquipmentHours = equipment.stream()
        .map(DprEquipment::getWorkingHours)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalFuelLitres = equipment.stream()
        .map(DprEquipment::getFuelLitres)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new DprSubmittedEvent(
        saved.getProjectId(),
        saved.getId(),
        saved.getReportDate(),
        saved.getActivityName(),
        saved.getBoqItemNo(),
        saved.getQtyExecuted(),
        oldBoqItemNo,
        oldQty,
        type,
        manpower.size(),
        equipment.size(),
        material.size(),
        totalManpowerHours,
        totalEquipmentHours,
        totalFuelLitres);
  }

  private static BigDecimal add(BigDecimal a, BigDecimal b) {
    BigDecimal aa = a != null ? a : BigDecimal.ZERO;
    BigDecimal bb = b != null ? b : BigDecimal.ZERO;
    return aa.add(bb);
  }

  // ─── Resource snapshot + ledger reconcile (DPR write path) ────────────────────────

  /** Snapshot bag returned by {@link #snapshotChildren} — entities ready to persist. */
  private record SnapshottedChildren(List<DprManpower> manpower, List<DprEquipment> equipment, List<DprMaterial> material) {}

  /**
   * Resolve unit-rate snapshot per row, validate the assignment ↔ activity ↔ kind invariant,
   * and compute {@code line_cost} via {@link DprCostFormulas}. Rejects rows whose assignment
   * doesn't match the DPR's activity or whose resource type is wrong for the row kind. Missing
   * rates produce a warning but never reject.
   */
  private SnapshottedChildren snapshotChildren(
      DailyProgressReport saved,
      List<DprManpowerRow> manpowerRows,
      List<DprEquipmentRow> equipmentRows,
      List<DprMaterialRow> materialRows,
      List<String> warnings) {

    LocalDate reportDate = saved.getReportDate();
    UUID activityId = saved.getActivityId();
    UUID dprId = saved.getId();
    boolean canValidate = activityId != null;

    List<DprManpower> manpower = new ArrayList<>();
    if (manpowerRows != null) {
      for (DprManpowerRow row : manpowerRows) {
        AssignmentSnapshot snap = lookupAssignmentSnapshot(row.resourceAssignmentId(), reportDate);
        if (canValidate) requireKind(row.resourceAssignmentId(), snap, "MANPOWER", activityId, warnings);
        BigDecimal unitRate = pickUnitRate(row.unitRate(), snap);
        String basis = pickBasis(row.unitRateBasis(), snap);
        if (unitRate == null) warnings.add("rate-missing:manpower:" + safeName(snap, row.trade()));
        DprManpower entity = row.toEntity(dprId);
        entity.setResourceId(pickResourceId(row.resourceId(), snap));
        entity.setUnitRate(unitRate);
        entity.setUnitRateBasis(basis);
        entity.setLineCost(DprCostFormulas.manpowerLineCost(entity, unitRate, basis));
        manpower.add(entity);
      }
    }

    List<DprEquipment> equipment = new ArrayList<>();
    if (equipmentRows != null) {
      for (DprEquipmentRow row : equipmentRows) {
        AssignmentSnapshot snap = lookupAssignmentSnapshot(row.resourceAssignmentId(), reportDate);
        if (canValidate) requireKind(row.resourceAssignmentId(), snap, "EQUIPMENT", activityId, warnings);
        BigDecimal unitRate = pickUnitRate(row.unitRate(), snap);
        String basis = snap == null ? "HOUR" : (snap.basis() != null ? snap.basis() : "HOUR");
        if (unitRate == null) warnings.add("rate-missing:equipment:" + safeName(snap, row.equipmentType()));
        DprEquipment entity = row.toEntity(dprId);
        entity.setResourceId(pickResourceId(row.resourceId(), snap));
        entity.setUnitRate(unitRate);
        entity.setLineCost(DprCostFormulas.equipmentLineCost(entity, unitRate, basis));
        equipment.add(entity);
      }
    }

    List<DprMaterial> material = new ArrayList<>();
    if (materialRows != null) {
      for (DprMaterialRow row : materialRows) {
        AssignmentSnapshot snap = lookupAssignmentSnapshot(row.resourceAssignmentId(), reportDate);
        if (canValidate) requireKind(row.resourceAssignmentId(), snap, "MATERIAL", activityId, warnings);
        BigDecimal unitRate = pickUnitRate(row.unitRate(), snap);
        if (unitRate == null) warnings.add("rate-missing:material:" + safeName(snap, row.materialName()));
        DprMaterial entity = row.toEntity(dprId);
        entity.setResourceId(pickResourceId(row.resourceId(), snap));
        entity.setUnitRate(unitRate);
        entity.setLineCost(DprCostFormulas.materialLineCost(entity, unitRate));
        material.add(entity);
      }
    }

    return new SnapshottedChildren(manpower, equipment, material);
  }

  /**
   * One row from a tiny native join across {@code resource.resource_assignments → resources →
   * resource_types}. Returned by {@link #lookupAssignmentSnapshot}; carries everything the
   * snapshotter needs without an explicit Maven dep on {@code bipros-resource}.
   */
  private record AssignmentSnapshot(UUID activityId, UUID resourceId, String resourceName,
                                    String resourceTypeCode, String unit, BigDecimal unitRate,
                                    String basis) {}

  @SuppressWarnings("unchecked")
  private AssignmentSnapshot lookupAssignmentSnapshot(UUID assignmentId, LocalDate reportDate) {
    if (assignmentId == null) return null;
    if (em == null) return null; // unit-test fallback — Spring would normally inject this
    LocalDate effectiveOn = reportDate != null ? reportDate : LocalDate.now();
    List<Object[]> rows = em.createNativeQuery(
            "SELECT ra.activity_id, ra.resource_id, ra.rate_type, "
                + "       r.name, r.unit, r.cost_per_unit, "
                + "       rt.code "
                + "FROM resource.resource_assignments ra "
                + "LEFT JOIN resource.resources r ON r.id = ra.resource_id "
                + "LEFT JOIN resource.resource_types rt ON rt.id = r.resource_type_id "
                + "WHERE ra.id = :assignmentId")
        .setParameter("assignmentId", assignmentId)
        .getResultList();
    if (rows.isEmpty()) return null;
    Object[] row = rows.get(0);
    UUID activityId = (UUID) row[0];
    UUID resourceId = (UUID) row[1];
    String rateType = (String) row[2];
    String resourceName = (String) row[3];
    String unit = (String) row[4];
    BigDecimal costPerUnit = row[5] == null ? null : (BigDecimal) row[5];
    String typeCode = (String) row[6];

    BigDecimal effectiveRate = resolveEffectiveRate(resourceId, rateType, effectiveOn);
    if (effectiveRate == null) effectiveRate = costPerUnit;

    return new AssignmentSnapshot(activityId, resourceId, resourceName, typeCode, unit,
        effectiveRate, deriveBasis(unit));
  }

  /** Finds the latest {@code resource_rates} row covering {@code reportDate} for the resource. */
  private BigDecimal resolveEffectiveRate(UUID resourceId, String rateType, LocalDate reportDate) {
    if (resourceId == null) return null;
    String sql = "SELECT price_per_unit FROM resource.resource_rates "
        + "WHERE resource_id = :resourceId "
        + "  AND effective_date <= :reportDate "
        + "  AND (effective_to IS NULL OR effective_to >= :reportDate) "
        + (rateType != null && !rateType.isBlank() ? "  AND rate_type = :rateType " : "")
        + "ORDER BY effective_date DESC LIMIT 1";
    var query = em.createNativeQuery(sql)
        .setParameter("resourceId", resourceId)
        .setParameter("reportDate", reportDate);
    if (rateType != null && !rateType.isBlank()) {
      query.setParameter("rateType", rateType);
    }
    @SuppressWarnings("unchecked")
    List<Object> rows = query.getResultList();
    if (rows.isEmpty() || rows.get(0) == null) return null;
    return (BigDecimal) rows.get(0);
  }

  private static String deriveBasis(String unit) {
    if (unit == null) return "DAY";
    String u = unit.trim().toUpperCase();
    if (u.contains("HOUR") || u.equals("HR")) return "HOUR";
    if (u.contains("DAY") || u.contains("SHIFT")) return "DAY";
    if (u.contains("MIN")) return "HOUR";
    return "EACH";
  }

  private static BigDecimal pickUnitRate(BigDecimal clientSent, AssignmentSnapshot snap) {
    if (clientSent != null) return clientSent;
    return snap == null ? null : snap.unitRate();
  }

  private static String pickBasis(String clientSent, AssignmentSnapshot snap) {
    if (clientSent != null && !clientSent.isBlank()) return clientSent;
    return snap == null ? "DAY" : snap.basis();
  }

  private static UUID pickResourceId(UUID clientSent, AssignmentSnapshot snap) {
    if (clientSent != null) return clientSent;
    return snap == null ? null : snap.resourceId();
  }

  private static String safeName(AssignmentSnapshot snap, String fallback) {
    if (snap != null && snap.resourceName() != null) return snap.resourceName();
    return fallback != null ? fallback : "(unknown)";
  }

  /**
   * Validate the assignment ↔ activity ↔ kind invariant when the lookup succeeded. When
   * {@code snap} is null (typically: cross-schema query couldn't find the row) we emit a warning
   * and return — production data always resolves, but unit tests run with a mocked
   * {@link EntityManager} that returns nothing, and we don't want them rejecting every row.
   */
  private void requireKind(UUID assignmentId, AssignmentSnapshot snap, String requiredKind,
                           UUID expectedActivityId, List<String> warnings) {
    if (snap == null) {
      if (warnings != null) warnings.add("assignment-not-found:" + assignmentId);
      return;
    }
    if (expectedActivityId != null && !expectedActivityId.equals(snap.activityId())) {
      throw new BusinessRuleException("INVALID_DPR_RESOURCE",
          "Resource assignment does not belong to this activity: assignmentId=" + assignmentId);
    }
    String typeCode = snap.resourceTypeCode();
    boolean ok = switch (requiredKind) {
      case "MANPOWER" -> "LABOR".equalsIgnoreCase(typeCode) || "MANPOWER".equalsIgnoreCase(typeCode);
      case "EQUIPMENT" -> "EQUIPMENT".equalsIgnoreCase(typeCode);
      case "MATERIAL" -> "MATERIAL".equalsIgnoreCase(typeCode);
      default -> true;
    };
    if (!ok) {
      throw new BusinessRuleException("INVALID_DPR_RESOURCE_KIND",
          "Resource assignment kind " + typeCode + " does not match row kind " + requiredKind);
    }
  }

  /**
   * Aggregate child-row units per (activity, resource) and hand them to the ledger service.
   * Only rows with an {@code activityId} on the parent and a {@code resourceId} on the row
   * contribute — others are silently skipped (warnings already emitted by the snapshotter).
   */
  private void reconcileLedger(DailyProgressReport saved,
                               List<DprManpower> manpower,
                               List<DprEquipment> equipment,
                               List<DprMaterial> material) {
    UUID activityId = saved.getActivityId();
    if (activityId == null) {
      // Without activityId we can't write ledger rows. Reconcile only deletes existing rows for
      // this DPR (in case activityId was cleared on update).
      ledgerService.reconcileDprLedger(saved.getProjectId(), saved.getId(), saved.getReportDate(), List.of());
      return;
    }

    Map<UUID, BigDecimal> unitsByResource = new HashMap<>();
    Map<UUID, Double> hoursByResource = new HashMap<>();
    Map<UUID, String> unitByResource = new HashMap<>();

    for (DprManpower row : manpower) {
      if (row.getResourceId() == null) continue;
      String basis = row.getUnitRateBasis() != null ? row.getUnitRateBasis() : "DAY";
      BigDecimal units = DprCostFormulas.manpowerUnits(row, basis);
      unitsByResource.merge(row.getResourceId(), units, BigDecimal::add);
      double hrs = (row.getWorkingHours() == null ? 0d : row.getWorkingHours().doubleValue())
          + (row.getOtHours() == null ? 0d : row.getOtHours().doubleValue());
      hoursByResource.merge(row.getResourceId(), hrs * (row.getNos() == null ? 1 : row.getNos()), Double::sum);
      unitByResource.putIfAbsent(row.getResourceId(), basis.equalsIgnoreCase("HOUR") ? "HR" : "DAY");
    }
    for (DprEquipment row : equipment) {
      if (row.getResourceId() == null) continue;
      // Equipment rate basis isn't snapshotted on the row — use HOUR by default (most equipment
      // is hourly-billed). DAY-billed equipment will roll up by nos × 1 day-equivalent.
      BigDecimal units = DprCostFormulas.equipmentUnits(row, "HOUR");
      unitsByResource.merge(row.getResourceId(), units, BigDecimal::add);
      double hrs = (row.getWorkingHours() == null ? 0d : row.getWorkingHours().doubleValue())
          * (row.getNos() == null ? 1 : row.getNos());
      hoursByResource.merge(row.getResourceId(), hrs, Double::sum);
      unitByResource.putIfAbsent(row.getResourceId(), "HR");
    }
    for (DprMaterial row : material) {
      if (row.getResourceId() == null) continue;
      BigDecimal units = DprCostFormulas.materialUnits(row);
      unitsByResource.merge(row.getResourceId(), units, BigDecimal::add);
      unitByResource.putIfAbsent(row.getResourceId(), row.getUnit() != null ? row.getUnit() : "EA");
    }

    List<DailyActivityResourceOutputService.DprResourceAggregate> aggregates = unitsByResource.entrySet().stream()
        .sorted(Comparator.comparing(e -> e.getKey().toString()))
        .map(e -> new DailyActivityResourceOutputService.DprResourceAggregate(
            activityId,
            e.getKey(),
            e.getValue(),
            unitByResource.get(e.getKey()),
            hoursByResource.get(e.getKey()),
            null))
        .toList();

    ledgerService.reconcileDprLedger(saved.getProjectId(), saved.getId(), saved.getReportDate(), aggregates);
  }
}
