package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.CapacityUtilizationReport.Budgeted;
import com.bipros.reporting.application.dto.SupervisorPerformanceComparison;
import com.bipros.reporting.application.dto.SupervisorPerformanceComparison.EquipmentDelta;
import com.bipros.reporting.application.dto.SupervisorPerformanceComparison.TradeDelta;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport.ActivityDrillDown;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport.EquipmentRollup;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport.PlannedActuals;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport.ProductivityNorms;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport.ResourceLine;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport.Summary;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport.TradeRollup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supervisor-aware capacity utilization. Builds the SC180 Resource Productivity Report shape
 * (per-trade Manpower Utilization, per-equipment-type Equipment Utilization, per-activity
 * drill-down) by reading {@code project.dpr_manpower} / {@code project.dpr_equipment} directly
 * — keyed by supervisor via the parent {@code daily_progress_reports.supervisor_user_id}
 * (Phase 4.4: the legacy {@code supervisor_resource_id} column was dropped by migration 091).
 *
 * <p>{@code supervisorUserId == null} collapses the filter to project-wide. The page can use
 * the same endpoint for both views.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupervisorPerformanceReportService {

  private static final double DEFAULT_HOURS_PER_DAY = 8.0;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal UTILIZATION_CAP = BigDecimal.valueOf(999);

  @PersistenceContext private EntityManager em;

  private final ProductivityNormResolver normResolver;

  // ─── Public API ─────────────────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public SupervisorPerformanceReport build(
      UUID projectId, UUID supervisorUserId,
      LocalDate fromDate, LocalDate toDate, int workDays) {

    LocalDate today = LocalDate.now();
    LocalDate effectiveTo = toDate == null ? today : toDate;
    LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
    int effectiveWorkDays = workDays > 0 ? workDays : 26;

    String supervisorName = supervisorUserId == null ? null
        : resolveSupervisorName(projectId, supervisorUserId);

    List<ManpowerCellRow> manpowerCells =
        fetchManpowerCells(projectId, supervisorUserId, effectiveFrom, effectiveTo);
    List<EquipmentCellRow> equipmentCells =
        fetchEquipmentCells(projectId, supervisorUserId, effectiveFrom, effectiveTo);
    Map<UUID, ActivityMeta> activityMeta =
        fetchActivityMeta(projectId, supervisorUserId, effectiveFrom, effectiveTo);

    // Resolve norms once per (workActivityId, resourceTypeId) to avoid hammering the DB inside
    // the inner loops. Both the trade rollup and the activity drill-down consume the same map.
    // Cache norms with kind-aware keys so a MANPOWER lookup and an EQUIPMENT lookup for the
    // same (work_activity, resource_type) don't collide and don't accidentally cross-apply.
    Map<NormCacheKey, Budgeted> normCache = new HashMap<>();
    for (ManpowerCellRow c : manpowerCells) {
      normCache.computeIfAbsent(
          new NormCacheKey(c.workActivityId(), c.resourceTypeId(), "MANPOWER"),
          k -> normResolver.resolveByResourceType(k.workActivityId(), k.resourceTypeId(), k.normType()));
    }
    for (EquipmentCellRow c : equipmentCells) {
      normCache.computeIfAbsent(
          new NormCacheKey(c.workActivityId(), c.resourceTypeId(), "EQUIPMENT"),
          k -> normResolver.resolveByResourceType(k.workActivityId(), k.resourceTypeId(), k.normType()));
    }

    List<TradeRollup> tradeRollups = rollUpManpower(manpowerCells, normCache, effectiveWorkDays);
    List<EquipmentRollup> equipmentRollups = rollUpEquipment(equipmentCells, normCache, effectiveWorkDays);
    List<ActivityDrillDown> drillDown = buildDrillDown(manpowerCells, equipmentCells, normCache, activityMeta);

    return new SupervisorPerformanceReport(
        projectId, supervisorUserId, supervisorName,
        effectiveFrom, effectiveTo, effectiveWorkDays,
        new Summary(tradeRollups, equipmentRollups),
        drillDown);
  }

  @Transactional(readOnly = true)
  public SupervisorPerformanceComparison compare(
      UUID projectId, List<UUID> supervisorUserIds,
      LocalDate fromDate, LocalDate toDate, int workDays) {
    if (supervisorUserIds == null || supervisorUserIds.size() < 2) {
      throw new IllegalArgumentException("compare requires at least 2 supervisor ids");
    }

    List<SupervisorPerformanceReport> reports = new ArrayList<>(supervisorUserIds.size());
    for (UUID supId : supervisorUserIds) {
      reports.add(build(projectId, supId, fromDate, toDate, workDays));
    }

    LocalDate windowFrom = reports.get(0).fromDate();
    LocalDate windowTo = reports.get(0).toDate();
    int windowWorkDays = reports.get(0).workDays();

    Map<String, TradeDelta> tradeAcc = new LinkedHashMap<>();
    for (SupervisorPerformanceReport rep : reports) {
      for (TradeRollup tr : rep.summary().manpower()) {
        TradeDelta delta = tradeAcc.computeIfAbsent(tr.tradeKey(),
            k -> new TradeDelta(k, tr.tradeLabel(), new LinkedHashMap<>(), null, null));
        delta.bySupervisor().put(rep.supervisorUserId(), tr);
      }
    }
    List<TradeDelta> tradeDeltas = new ArrayList<>(tradeAcc.size());
    for (TradeDelta partial : tradeAcc.values()) {
      Map.Entry<UUID, TradeRollup> best = partial.bySupervisor().entrySet().stream()
          .filter(e -> e.getValue().utilizationPct() != null)
          .max(Comparator.comparing(e -> e.getValue().utilizationPct()))
          .orElse(null);
      tradeDeltas.add(new TradeDelta(
          partial.tradeKey(), partial.tradeLabel(), partial.bySupervisor(),
          best != null ? best.getValue().utilizationPct() : null,
          best != null ? best.getKey() : null));
    }

    Map<String, EquipmentDelta> eqAcc = new LinkedHashMap<>();
    for (SupervisorPerformanceReport rep : reports) {
      for (EquipmentRollup er : rep.summary().equipment()) {
        EquipmentDelta delta = eqAcc.computeIfAbsent(er.equipmentKey(),
            k -> new EquipmentDelta(k, er.equipmentLabel(), new LinkedHashMap<>(), null, null));
        delta.bySupervisor().put(rep.supervisorUserId(), er);
      }
    }
    List<EquipmentDelta> equipmentDeltas = new ArrayList<>(eqAcc.size());
    for (EquipmentDelta partial : eqAcc.values()) {
      Map.Entry<UUID, EquipmentRollup> best = partial.bySupervisor().entrySet().stream()
          .filter(e -> e.getValue().utilizationPct() != null)
          .max(Comparator.comparing(e -> e.getValue().utilizationPct()))
          .orElse(null);
      equipmentDeltas.add(new EquipmentDelta(
          partial.equipmentKey(), partial.equipmentLabel(), partial.bySupervisor(),
          best != null ? best.getValue().utilizationPct() : null,
          best != null ? best.getKey() : null));
    }

    return new SupervisorPerformanceComparison(
        projectId, windowFrom, windowTo, windowWorkDays, reports, tradeDeltas, equipmentDeltas);
  }

  // ─── Native queries (Q1, Q2, Q3) ────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private List<ManpowerCellRow> fetchManpowerCells(
      UUID projectId, UUID supervisorUserId, LocalDate fromDate, LocalDate toDate) {

    List<Object[]> raw = em.createNativeQuery(
            "SELECT "
                + "  COALESCE(rr.code, UPPER(TRIM(m.trade)))                       AS trade_key, "
                + "  COALESCE(rr.name, m.trade)                                    AS trade_label, "
                + "  r.resource_type_id                                            AS resource_type_id, "
                + "  d.activity_id                                                 AS activity_id, "
                + "  a.work_activity_id                                            AS work_activity_id, "
                + "  a.code                                                        AS activity_code, "
                + "  a.name                                                        AS activity_name, "
                + "  d.unit                                                        AS activity_unit, "
                + "  SUM(d.qty_executed)                                           AS qty, "
                + "  SUM(COALESCE(m.nos, 1) * COALESCE(m.working_hours, 0))        AS person_hours, "
                + "  SUM( "
                + "    COALESCE(m.line_cost, "
                + "      COALESCE(m.unit_rate, 0) * "
                + "      CASE "
                + "        WHEN m.unit_rate_basis = 'HOUR' "
                + "          THEN COALESCE(m.nos, 1) * COALESCE(m.working_hours, 0) "
                + "        ELSE COALESCE(m.nos, 1) "
                + "      END) "
                + "  )                                                              AS line_cost_total "
                + "FROM project.daily_progress_reports d "
                + "JOIN project.dpr_manpower m       ON m.dpr_id = d.id "
                + "LEFT JOIN resource.resources r    ON r.id = m.resource_id "
                + "LEFT JOIN resource.resource_roles rr ON rr.id = r.role_id "
                + "LEFT JOIN activity.activities a   ON a.id = d.activity_id "
                + "WHERE d.project_id = :projectId "
                + "  AND d.report_date BETWEEN :fromDate AND :toDate "
                + "  AND (CAST(:supervisorUserId AS uuid) IS NULL "
                + "       OR d.supervisor_user_id = CAST(:supervisorUserId AS uuid)) "
                + "GROUP BY trade_key, trade_label, r.resource_type_id, d.activity_id, "
                + "         a.work_activity_id, a.code, a.name, d.unit")
        .setParameter("projectId", projectId)
        .setParameter("fromDate", fromDate)
        .setParameter("toDate", toDate)
        .setParameter("supervisorUserId",
            supervisorUserId != null ? supervisorUserId.toString() : null)
        .getResultList();

    List<ManpowerCellRow> out = new ArrayList<>(raw.size());
    for (Object[] r : raw) {
      out.add(new ManpowerCellRow(
          (String) r[0], (String) r[1], (UUID) r[2],
          (UUID) r[3], (UUID) r[4],
          (String) r[5], (String) r[6], (String) r[7],
          toBigDecimal(r[8]), toBigDecimal(r[9]), toBigDecimal(r[10])));
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private List<EquipmentCellRow> fetchEquipmentCells(
      UUID projectId, UUID supervisorUserId, LocalDate fromDate, LocalDate toDate) {

    List<Object[]> raw = em.createNativeQuery(
            "SELECT "
                + "  COALESCE(rt.code, UPPER(TRIM(e.equipment_type)))             AS equipment_key, "
                + "  COALESCE(rt.name, e.equipment_type)                          AS equipment_label, "
                + "  r.resource_type_id                                           AS resource_type_id, "
                + "  d.activity_id                                                AS activity_id, "
                + "  a.work_activity_id                                           AS work_activity_id, "
                + "  a.code                                                       AS activity_code, "
                + "  a.name                                                       AS activity_name, "
                + "  d.unit                                                       AS activity_unit, "
                + "  SUM(d.qty_executed)                                          AS qty, "
                + "  SUM(COALESCE(e.nos, 1) * COALESCE(e.working_hours, 0))       AS machine_hours, "
                + "  SUM( "
                + "    COALESCE(e.line_cost, "
                + "      COALESCE(e.unit_rate, 0) * "
                + "      CASE "
                + "        WHEN e.unit_rate_basis = 'HOUR' "
                + "          THEN COALESCE(e.nos, 1) * COALESCE(e.working_hours, 0) "
                + "        ELSE COALESCE(e.nos, 1) "
                + "      END) "
                + "  )                                                             AS line_cost_total "
                + "FROM project.daily_progress_reports d "
                + "JOIN project.dpr_equipment e        ON e.dpr_id = d.id "
                + "LEFT JOIN resource.resources r      ON r.id = e.resource_id "
                + "LEFT JOIN resource.resource_types rt ON rt.id = r.resource_type_id "
                + "LEFT JOIN activity.activities a     ON a.id = d.activity_id "
                + "WHERE d.project_id = :projectId "
                + "  AND d.report_date BETWEEN :fromDate AND :toDate "
                + "  AND (CAST(:supervisorUserId AS uuid) IS NULL "
                + "       OR d.supervisor_user_id = CAST(:supervisorUserId AS uuid)) "
                + "GROUP BY equipment_key, equipment_label, r.resource_type_id, d.activity_id, "
                + "         a.work_activity_id, a.code, a.name, d.unit")
        .setParameter("projectId", projectId)
        .setParameter("fromDate", fromDate)
        .setParameter("toDate", toDate)
        .setParameter("supervisorUserId",
            supervisorUserId != null ? supervisorUserId.toString() : null)
        .getResultList();

    List<EquipmentCellRow> out = new ArrayList<>(raw.size());
    for (Object[] r : raw) {
      out.add(new EquipmentCellRow(
          (String) r[0], (String) r[1], (UUID) r[2],
          (UUID) r[3], (UUID) r[4],
          (String) r[5], (String) r[6], (String) r[7],
          toBigDecimal(r[8]), toBigDecimal(r[9]), toBigDecimal(r[10])));
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, ActivityMeta> fetchActivityMeta(
      UUID projectId, UUID supervisorUserId, LocalDate fromDate, LocalDate toDate) {

    List<Object[]> raw = em.createNativeQuery(
            "SELECT d.activity_id, "
                + "       MAX(d.activity_name)                                    AS activity_name, "
                + "       MAX(d.unit)                                             AS unit, "
                + "       SUM(d.qty_executed)                                     AS qty_total, "
                + "       STRING_AGG(DISTINCT NULLIF(d.remarks, ''), ' | ')       AS remarks "
                + "FROM project.daily_progress_reports d "
                + "WHERE d.project_id = :projectId "
                + "  AND d.report_date BETWEEN :fromDate AND :toDate "
                + "  AND d.activity_id IS NOT NULL "
                + "  AND (CAST(:supervisorUserId AS uuid) IS NULL "
                + "       OR d.supervisor_user_id = CAST(:supervisorUserId AS uuid)) "
                + "GROUP BY d.activity_id")
        .setParameter("projectId", projectId)
        .setParameter("fromDate", fromDate)
        .setParameter("toDate", toDate)
        .setParameter("supervisorUserId",
            supervisorUserId != null ? supervisorUserId.toString() : null)
        .getResultList();

    Map<UUID, ActivityMeta> out = new LinkedHashMap<>(raw.size());
    for (Object[] r : raw) {
      UUID actId = (UUID) r[0];
      out.put(actId, new ActivityMeta(actId, (String) r[1], (String) r[2],
          toBigDecimal(r[3]), (String) r[4]));
    }
    return out;
  }

  /**
   * Resolve the display name for a supervisor User UUID. Phase 4.4 — the id is a User FK now,
   * so we look up {@code public.users} first and fall back to the latest DPR's snapshot name
   * for off-roster (legacy / free-text) supervisors.
   */
  private String resolveSupervisorName(UUID projectId, UUID supervisorUserId) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery(
            "SELECT COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username), "
                + "       u.username "
                + "FROM public.users u WHERE u.id = :id")
        .setParameter("id", supervisorUserId)
        .setMaxResults(1)
        .getResultList();
    if (rows.isEmpty()) {
      // Fall back to the latest DPR's snapshot name.
      @SuppressWarnings("unchecked")
      List<Object> snap = em.createNativeQuery(
              "SELECT supervisor_name FROM project.daily_progress_reports "
                  + "WHERE project_id = :p AND supervisor_user_id = :id "
                  + "ORDER BY report_date DESC LIMIT 1")
          .setParameter("p", projectId)
          .setParameter("id", supervisorUserId)
          .getResultList();
      return snap.isEmpty() ? null : (String) snap.get(0);
    }
    return (String) rows.get(0)[0];
  }

  // ─── Roll-up logic ──────────────────────────────────────────────────────────────────────────

  private List<TradeRollup> rollUpManpower(
      List<ManpowerCellRow> cells, Map<NormCacheKey, Budgeted> normCache, int workDays) {

    Map<String, TradeAccumulator> byTrade = new LinkedHashMap<>();
    for (ManpowerCellRow c : cells) {
      TradeAccumulator acc = byTrade.computeIfAbsent(c.tradeKey(),
          k -> new TradeAccumulator(c.tradeKey(), c.tradeLabel()));
      Budgeted norm = normCache.get(new NormCacheKey(c.workActivityId(), c.resourceTypeId(), "MANPOWER"));
      BigDecimal cellActualDays = c.personHours() == null
          ? BigDecimal.ZERO
          : c.personHours().divide(BigDecimal.valueOf(DEFAULT_HOURS_PER_DAY), 6, RoundingMode.HALF_UP);
      BigDecimal cellBudgetedDays = (norm != null && norm.outputPerDay() != null
          && norm.outputPerDay().signum() > 0 && c.qty() != null)
          ? c.qty().divide(norm.outputPerDay(), 6, RoundingMode.HALF_UP)
          : null;

      acc.actualDays = acc.actualDays.add(cellActualDays);
      if (cellBudgetedDays != null) {
        acc.budgetedDays = (acc.budgetedDays == null) ? cellBudgetedDays : acc.budgetedDays.add(cellBudgetedDays);
      }
      if (c.lineCostTotal() != null) {
        acc.lineCostTotal = acc.lineCostTotal.add(c.lineCostTotal());
      }
      // Track the best norm source observed across cells for surfacing in the UI.
      if (norm != null && norm.source() != null && !"NONE".equals(norm.source())) {
        if (acc.normSource == null
            || normSourceRank(norm.source()) < normSourceRank(acc.normSource)) {
          acc.normSource = norm.source();
        }
      }
    }

    List<TradeRollup> out = new ArrayList<>(byTrade.size());
    for (TradeAccumulator a : byTrade.values()) {
      out.add(buildTradeRollup(a, workDays));
    }
    out.sort(Comparator.comparing(TradeRollup::tradeLabel, Comparator.nullsLast(String::compareToIgnoreCase)));
    return out;
  }

  private List<EquipmentRollup> rollUpEquipment(
      List<EquipmentCellRow> cells, Map<NormCacheKey, Budgeted> normCache, int workDays) {

    Map<String, TradeAccumulator> byEquipment = new LinkedHashMap<>();
    for (EquipmentCellRow c : cells) {
      TradeAccumulator acc = byEquipment.computeIfAbsent(c.equipmentKey(),
          k -> new TradeAccumulator(c.equipmentKey(), c.equipmentLabel()));
      Budgeted norm = normCache.get(new NormCacheKey(c.workActivityId(), c.resourceTypeId(), "EQUIPMENT"));
      BigDecimal cellActualDays = c.machineHours() == null
          ? BigDecimal.ZERO
          : c.machineHours().divide(BigDecimal.valueOf(DEFAULT_HOURS_PER_DAY), 6, RoundingMode.HALF_UP);
      BigDecimal cellBudgetedDays = (norm != null && norm.outputPerDay() != null
          && norm.outputPerDay().signum() > 0 && c.qty() != null)
          ? c.qty().divide(norm.outputPerDay(), 6, RoundingMode.HALF_UP)
          : null;

      acc.actualDays = acc.actualDays.add(cellActualDays);
      if (cellBudgetedDays != null) {
        acc.budgetedDays = (acc.budgetedDays == null) ? cellBudgetedDays : acc.budgetedDays.add(cellBudgetedDays);
      }
      if (c.lineCostTotal() != null) {
        acc.lineCostTotal = acc.lineCostTotal.add(c.lineCostTotal());
      }
      if (norm != null && norm.source() != null && !"NONE".equals(norm.source())) {
        if (acc.normSource == null
            || normSourceRank(norm.source()) < normSourceRank(acc.normSource)) {
          acc.normSource = norm.source();
        }
      }
    }

    List<EquipmentRollup> out = new ArrayList<>(byEquipment.size());
    for (TradeAccumulator a : byEquipment.values()) {
      out.add(buildEquipmentRollup(a, workDays));
    }
    out.sort(Comparator.comparing(EquipmentRollup::equipmentLabel, Comparator.nullsLast(String::compareToIgnoreCase)));
    return out;
  }

  private TradeRollup buildTradeRollup(TradeAccumulator a, int workDays) {
    BigDecimal mmRate = (a.actualDays.signum() > 0)
        ? a.lineCostTotal.divide(a.actualDays, 4, RoundingMode.HALF_UP)
        : null;
    BigDecimal utilizationPct = computeUtilizationPct(a.budgetedDays, a.actualDays);
    BigDecimal costImplication = (mmRate != null && a.budgetedDays != null)
        ? a.actualDays.subtract(a.budgetedDays).multiply(mmRate).setScale(2, RoundingMode.HALF_UP)
        : null;
    BigDecimal actualNos = (workDays > 0) ? a.actualDays.divide(BigDecimal.valueOf(workDays), 2, RoundingMode.HALF_UP) : null;
    BigDecimal budgetedNos = (a.budgetedDays != null && workDays > 0)
        ? a.budgetedDays.divide(BigDecimal.valueOf(workDays), 2, RoundingMode.HALF_UP)
        : null;
    return new TradeRollup(
        a.key, a.label,
        mmRate != null ? mmRate.setScale(2, RoundingMode.HALF_UP) : null,
        a.budgetedDays != null ? a.budgetedDays.setScale(2, RoundingMode.HALF_UP) : null,
        budgetedNos,
        a.actualDays.setScale(2, RoundingMode.HALF_UP),
        actualNos,
        utilizationPct,
        costImplication,
        a.normSource != null ? a.normSource : "NONE");
  }

  private EquipmentRollup buildEquipmentRollup(TradeAccumulator a, int workDays) {
    BigDecimal hourRate = (a.actualDays.signum() > 0)
        ? a.lineCostTotal.divide(a.actualDays, 4, RoundingMode.HALF_UP)
        : null;
    BigDecimal utilizationPct = computeUtilizationPct(a.budgetedDays, a.actualDays);
    BigDecimal costImplication = (hourRate != null && a.budgetedDays != null)
        ? a.actualDays.subtract(a.budgetedDays).multiply(hourRate).setScale(2, RoundingMode.HALF_UP)
        : null;
    BigDecimal actualNos = (workDays > 0) ? a.actualDays.divide(BigDecimal.valueOf(workDays), 2, RoundingMode.HALF_UP) : null;
    BigDecimal budgetedNos = (a.budgetedDays != null && workDays > 0)
        ? a.budgetedDays.divide(BigDecimal.valueOf(workDays), 2, RoundingMode.HALF_UP)
        : null;
    return new EquipmentRollup(
        a.key, a.label,
        hourRate != null ? hourRate.setScale(2, RoundingMode.HALF_UP) : null,
        a.budgetedDays != null ? a.budgetedDays.setScale(2, RoundingMode.HALF_UP) : null,
        budgetedNos,
        a.actualDays.setScale(2, RoundingMode.HALF_UP),
        actualNos,
        utilizationPct,
        costImplication,
        a.normSource != null ? a.normSource : "NONE");
  }

  // ─── Activity drill-down ────────────────────────────────────────────────────────────────────

  private List<ActivityDrillDown> buildDrillDown(
      List<ManpowerCellRow> manpowerCells, List<EquipmentCellRow> equipmentCells,
      Map<NormCacheKey, Budgeted> normCache, Map<UUID, ActivityMeta> activityMeta) {

    // Aggregate at (activity, kind, resourceKey) so two cells with the same trade key but
    // different resource_type_ids (e.g. role "ROLE-HELPER" attached to two different LABOR types)
    // don't produce two ResourceLines per activity. React would then see duplicate keys and
    // fall back to a slow reconciliation path that thrashes the page.
    Map<UUID, Map<String, ResourceLineAccumulator>> byActivity = new LinkedHashMap<>();
    for (ManpowerCellRow c : manpowerCells) {
      if (c.activityId() == null) continue;
      Budgeted norm = normCache.get(new NormCacheKey(c.workActivityId(), c.resourceTypeId(), "MANPOWER"));
      mergeIntoActivity(byActivity, c.activityId(), "MANPOWER",
          c.tradeKey(), c.tradeLabel(), c.qty(), c.personHours(), norm);
    }
    for (EquipmentCellRow c : equipmentCells) {
      if (c.activityId() == null) continue;
      Budgeted norm = normCache.get(new NormCacheKey(c.workActivityId(), c.resourceTypeId(), "EQUIPMENT"));
      mergeIntoActivity(byActivity, c.activityId(), "EQUIPMENT",
          c.equipmentKey(), c.equipmentLabel(), c.qty(), c.machineHours(), norm);
    }

    Map<UUID, ActivityHeader> headers = new HashMap<>();
    for (ManpowerCellRow c : manpowerCells) {
      headers.putIfAbsent(c.activityId(),
          new ActivityHeader(c.activityCode(), c.activityName(), c.activityUnit()));
    }
    for (EquipmentCellRow c : equipmentCells) {
      headers.putIfAbsent(c.activityId(),
          new ActivityHeader(c.activityCode(), c.activityName(), c.activityUnit()));
    }

    List<ActivityDrillDown> out = new ArrayList<>(byActivity.size());
    for (Map.Entry<UUID, Map<String, ResourceLineAccumulator>> e : byActivity.entrySet()) {
      UUID actId = e.getKey();
      ActivityMeta meta = activityMeta.get(actId);
      ActivityHeader header = headers.get(actId);
      List<ResourceLine> lines = new ArrayList<>(e.getValue().size());
      for (ResourceLineAccumulator acc : e.getValue().values()) {
        lines.add(buildResourceLine(acc.kind, acc.resourceKey, acc.resourceLabel,
            acc.qty, acc.hoursTotal, acc.norm));
      }
      out.add(new ActivityDrillDown(
          actId,
          header != null ? header.code() : null,
          header != null ? header.name() : (meta != null ? meta.activityName() : null),
          header != null ? header.unit() : (meta != null ? meta.unit() : null),
          meta != null ? meta.qtyTotal() : null,
          lines,
          meta != null ? meta.remarks() : null));
    }
    out.sort(Comparator.comparing(
        a -> a.activityCode() != null ? a.activityCode() : "",
        Comparator.nullsLast(String::compareTo)));
    return out;
  }

  private static void mergeIntoActivity(
      Map<UUID, Map<String, ResourceLineAccumulator>> byActivity,
      UUID activityId, String kind, String resourceKey, String resourceLabel,
      BigDecimal qty, BigDecimal hoursTotal, Budgeted norm) {
    Map<String, ResourceLineAccumulator> linesByKey =
        byActivity.computeIfAbsent(activityId, k -> new LinkedHashMap<>());
    String dedupeKey = kind + "::" + resourceKey;
    ResourceLineAccumulator acc = linesByKey.get(dedupeKey);
    if (acc == null) {
      linesByKey.put(dedupeKey, new ResourceLineAccumulator(kind, resourceKey, resourceLabel,
          qty == null ? BigDecimal.ZERO : qty,
          hoursTotal == null ? BigDecimal.ZERO : hoursTotal,
          norm));
    } else {
      if (qty != null) acc.qty = acc.qty.add(qty);
      if (hoursTotal != null) acc.hoursTotal = acc.hoursTotal.add(hoursTotal);
      // Preserve the first non-NONE norm encountered. Different resource_type_ids inside the
      // same trade typically share the same type-level norm, so this is harmless; if they
      // genuinely differ, the first-wins choice is documented as a known limitation.
      if ((acc.norm == null || acc.norm.source() == null || "NONE".equals(acc.norm.source()))
          && norm != null) {
        acc.norm = norm;
      }
    }
  }

  private static final class ResourceLineAccumulator {
    final String kind;
    final String resourceKey;
    final String resourceLabel;
    BigDecimal qty;
    BigDecimal hoursTotal;
    Budgeted norm;
    ResourceLineAccumulator(String kind, String resourceKey, String resourceLabel,
                            BigDecimal qty, BigDecimal hoursTotal, Budgeted norm) {
      this.kind = kind;
      this.resourceKey = resourceKey;
      this.resourceLabel = resourceLabel;
      this.qty = qty;
      this.hoursTotal = hoursTotal;
      this.norm = norm;
    }
  }

  private ResourceLine buildResourceLine(
      String kind, String key, String label,
      BigDecimal qty, BigDecimal hoursTotal, Budgeted norm) {

    BigDecimal actualDays = (hoursTotal != null)
        ? hoursTotal.divide(BigDecimal.valueOf(DEFAULT_HOURS_PER_DAY), 6, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    BigDecimal budgetDays = (norm != null && norm.outputPerDay() != null
        && norm.outputPerDay().signum() > 0 && qty != null)
        ? qty.divide(norm.outputPerDay(), 6, RoundingMode.HALF_UP)
        : null;
    BigDecimal actualsFtm = (qty != null && actualDays.signum() > 0)
        ? qty.divide(actualDays, 4, RoundingMode.HALF_UP)
        : null;
    BigDecimal utilizationPct = computeUtilizationPct(budgetDays, actualDays);

    PlannedActuals actuals = new PlannedActuals(
        qty != null ? qty.setScale(2, RoundingMode.HALF_UP) : null,
        budgetDays != null ? budgetDays.setScale(2, RoundingMode.HALF_UP) : null,
        actualDays.setScale(2, RoundingMode.HALF_UP),
        utilizationPct);

    // MVP: planMonth == actualMonth (we don't have a separate planning store yet).
    PlannedActuals plan = new PlannedActuals(
        qty != null ? qty.setScale(2, RoundingMode.HALF_UP) : null,
        budgetDays != null ? budgetDays.setScale(2, RoundingMode.HALF_UP) : null,
        budgetDays != null ? budgetDays.setScale(2, RoundingMode.HALF_UP) : null,
        budgetDays != null ? BigDecimal.valueOf(100) : null);

    ProductivityNorms norms = new ProductivityNorms(
        norm != null ? norm.outputPerDay() : null,
        null,
        actualsFtm != null ? actualsFtm.setScale(2, RoundingMode.HALF_UP) : null,
        norm != null ? norm.source() : "NONE");

    return new ResourceLine(kind, key, label, norms, plan, actuals);
  }

  // ─── Math helpers (package-private for tests) ──────────────────────────────────────────────

  static BigDecimal computeUtilizationPct(BigDecimal budgetedDays, BigDecimal actualDays) {
    if (budgetedDays == null || actualDays == null || actualDays.signum() <= 0) {
      return null;
    }
    BigDecimal pct = budgetedDays.divide(actualDays, 8, RoundingMode.HALF_UP).multiply(HUNDRED);
    return pct.min(UTILIZATION_CAP).setScale(2, RoundingMode.HALF_UP);
  }

  static BigDecimal computeCostImplication(
      BigDecimal actualDays, BigDecimal budgetedDays, BigDecimal rate) {
    if (rate == null || budgetedDays == null || actualDays == null) return null;
    return actualDays.subtract(budgetedDays).multiply(rate).setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal toBigDecimal(Object o) {
    if (o == null) return null;
    if (o instanceof BigDecimal bd) return bd;
    if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
    return null;
  }

  private static int normSourceRank(String source) {
    if (source == null) return 99;
    return switch (source) {
      case "SPECIFIC_RESOURCE" -> 0;
      case "RESOURCE_TYPE" -> 1;
      case "RESOURCE_LEGACY" -> 2;
      default -> 99;
    };
  }

  // ─── Internal record types ──────────────────────────────────────────────────────────────────

  private record ManpowerCellRow(
      String tradeKey, String tradeLabel, UUID resourceTypeId,
      UUID activityId, UUID workActivityId,
      String activityCode, String activityName, String activityUnit,
      BigDecimal qty, BigDecimal personHours, BigDecimal lineCostTotal) {}

  private record EquipmentCellRow(
      String equipmentKey, String equipmentLabel, UUID resourceTypeId,
      UUID activityId, UUID workActivityId,
      String activityCode, String activityName, String activityUnit,
      BigDecimal qty, BigDecimal machineHours, BigDecimal lineCostTotal) {}

  private record ActivityMeta(
      UUID activityId, String activityName, String unit, BigDecimal qtyTotal, String remarks) {}

  private record ActivityHeader(String code, String name, String unit) {}

  private record NormCacheKey(UUID workActivityId, UUID resourceTypeId, String normType) {}

  private static final class TradeAccumulator {
    final String key;
    final String label;
    BigDecimal actualDays = BigDecimal.ZERO;
    BigDecimal budgetedDays = null;     // null = no norm available for any contributing cell
    BigDecimal lineCostTotal = BigDecimal.ZERO;
    String normSource = null;

    TradeAccumulator(String key, String label) {
      this.key = key;
      this.label = label;
    }
  }
}
