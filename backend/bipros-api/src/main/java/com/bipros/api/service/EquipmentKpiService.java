package com.bipros.api.service;

import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprEquipment;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprEquipmentRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceEquipmentDetails;
import com.bipros.resource.domain.model.ResourceOwnership;
import com.bipros.resource.domain.repository.ResourceEquipmentDetailsRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregator KPI service for equipment metrics. Reads exclusively from DPR + child rows
 * (daily_progress_reports, dpr_equipment) — both Daily Outputs (DAR) and the standalone
 * resource.equipment_logs ledger are deprecated and no longer consulted. Op/idle/breakdown
 * hours and fuel come from {@code dpr_equipment}; per-machine output qty is derived from the
 * parent DPR's {@code qty_executed} via a proportional split on equipment hours within the
 * same DPR.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EquipmentKpiService {

  private static final String EQUIPMENT_TYPE_CODE = "EQUIPMENT";
  private static final double DEFAULT_IDLE_THRESHOLD_HOURS = 2d;
  private static final int DEFAULT_SERVICE_LOOKAHEAD_DAYS = 7;

  private final ResourceRepository resourceRepository;
  private final ResourceEquipmentDetailsRepository equipmentDetailsRepository;
  private final DailyProgressReportRepository dprRepository;
  private final DprEquipmentRepository dprEquipmentRepository;

  // ---------- Response shapes (unchanged) ----------

  public record EquipmentKpiResponse(
      UUID projectId,
      LocalDate from,
      LocalDate to,
      List<UtilizationRow> utilization,
      List<IdleAlertRow> idleAlerts,
      List<FuelPerOutputRow> fuelPerOutput,
      List<AvailabilityPerformanceRow> availabilityPerformance,
      List<OwnedRentedSlice> ownedVsRented,
      List<ServiceDueRow> serviceDue,
      double mechanicalAvailabilityPct,
      double equipmentProductivityIndexPct
  ) {}

  public record UtilizationRow(
      UUID resourceId,
      String resourceCode,
      String resourceName,
      double operatingHours,
      double idleHours,
      double breakdownHours,
      double utilizationPct,
      double mechanicalAvailabilityPct
  ) {}

  public record IdleAlertRow(
      UUID resourceId,
      String resourceCode,
      String resourceName,
      LocalDate logDate,
      double idleHours
  ) {}

  public record FuelPerOutputRow(
      UUID resourceId,
      String resourceCode,
      String resourceName,
      double fuelConsumed,
      double qtyExecuted,
      double fuelPerOutput
  ) {}

  public record AvailabilityPerformanceRow(
      UUID resourceId,
      String resourceCode,
      String resourceName,
      double availability,
      double performance
  ) {}

  public record OwnedRentedSlice(
      String ownershipType,
      double operatingHours,
      double cost
  ) {}

  public record ServiceDueRow(
      UUID resourceId,
      String resourceCode,
      String resourceName,
      LocalDate nextServiceDate,
      long daysUntilService
  ) {}

  /** Helper bag for one DPR-equipment row enriched with the parent DPR's date/qty share. */
  private record EnrichedDprEquipment(
      DprEquipment row,
      LocalDate logDate,
      double attributedQty
  ) {}

  // ---------- Public API ----------

  @Transactional(readOnly = true)
  public EquipmentKpiResponse compute(UUID projectId, LocalDate from, LocalDate to) {
    List<DailyProgressReport> dprs = dprRepository
        .findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(projectId, from, to);
    if (dprs.isEmpty()) {
      return emptyResponse(projectId, from, to);
    }
    Set<UUID> dprIds = dprs.stream().map(DailyProgressReport::getId).collect(Collectors.toSet());
    List<DprEquipment> equipmentRows = dprEquipmentRepository.findByDprIdIn(dprIds);
    Map<UUID, List<DprEquipment>> equipmentByDpr = equipmentRows.stream()
        .collect(Collectors.groupingBy(DprEquipment::getDprId));

    // Enrich each dpr_equipment row with the parent DPR's date and an attributed qty
    // (parent qty × this row's hours ÷ Σ row hours for the same DPR).
    Map<UUID, DailyProgressReport> dprsById = dprs.stream()
        .collect(Collectors.toMap(DailyProgressReport::getId, d -> d, (a, b) -> a));
    List<EnrichedDprEquipment> enriched = enrichEquipmentRows(equipmentByDpr, dprsById);

    Set<UUID> resourceIds = equipmentRows.stream()
        .map(DprEquipment::getResourceId)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    Map<UUID, Resource> resourcesById = resourceRepository.findAllById(resourceIds).stream()
        .collect(Collectors.toMap(Resource::getId, r -> r, (a, b) -> a));
    Map<UUID, ResourceEquipmentDetails> detailsById =
        equipmentDetailsRepository.findAllById(resourceIds).stream()
            .collect(Collectors.toMap(ResourceEquipmentDetails::getResourceId, d -> d, (a, b) -> a));

    List<UtilizationRow> utilization = computeUtilization(enriched, resourcesById);
    List<IdleAlertRow> idleAlerts = computeIdleAlerts(enriched, resourcesById);
    List<FuelPerOutputRow> fuelPerOutput = computeFuelPerOutput(enriched, resourcesById);
    List<AvailabilityPerformanceRow> availPerf =
        computeAvailabilityPerformance(enriched, resourcesById, detailsById);
    List<OwnedRentedSlice> ownedVsRented =
        computeOwnedVsRented(enriched, resourcesById, detailsById);
    List<ServiceDueRow> serviceDue = computeServiceDue(projectId, resourcesById, detailsById);

    double maPct = computeMechanicalAvailabilityHeadline(utilization);
    double epiPct = computeEpiHeadline(availPerf);

    return new EquipmentKpiResponse(
        projectId, from, to, utilization, idleAlerts, fuelPerOutput,
        availPerf, ownedVsRented, serviceDue,
        round4(maPct),
        round4(epiPct));
  }

  // ---------- Enrichment ----------

  /**
   * Distribute each DPR's parent qty_executed across its equipment rows by working-hours share.
   * If the DPR has no working hours captured, every row gets 0 attributed qty (KPI tiles show
   * "—" for that machine).
   */
  private List<EnrichedDprEquipment> enrichEquipmentRows(
      Map<UUID, List<DprEquipment>> equipmentByDpr,
      Map<UUID, DailyProgressReport> dprsById) {
    List<EnrichedDprEquipment> out = new ArrayList<>();
    for (Map.Entry<UUID, List<DprEquipment>> e : equipmentByDpr.entrySet()) {
      DailyProgressReport dpr = dprsById.get(e.getKey());
      if (dpr == null) continue;
      double parentQty = dpr.getQtyExecuted() != null ? dpr.getQtyExecuted().doubleValue() : 0d;
      double totalHours = e.getValue().stream()
          .mapToDouble(r -> r.getWorkingHours() != null ? r.getWorkingHours().doubleValue() : 0d)
          .sum();
      for (DprEquipment row : e.getValue()) {
        double rowHours = row.getWorkingHours() != null ? row.getWorkingHours().doubleValue() : 0d;
        double attributedQty = totalHours > 0 ? parentQty * (rowHours / totalHours) : 0d;
        out.add(new EnrichedDprEquipment(row, dpr.getReportDate(), attributedQty));
      }
    }
    return out;
  }

  // ---------- Utilisation + KPI 4.1 (Mechanical Availability per machine) ----------

  private List<UtilizationRow> computeUtilization(
      List<EnrichedDprEquipment> enriched,
      Map<UUID, Resource> resourcesById) {
    Map<UUID, double[]> agg = new HashMap<>(); // [op, idle, breakdown]
    for (EnrichedDprEquipment e : enriched) {
      DprEquipment l = e.row();
      if (l.getResourceId() == null) continue;
      double[] acc = agg.computeIfAbsent(l.getResourceId(), k -> new double[3]);
      if (l.getWorkingHours() != null) acc[0] += l.getWorkingHours().doubleValue();
      if (l.getIdleHours() != null) acc[1] += l.getIdleHours().doubleValue();
      if (l.getBreakdownHours() != null) acc[2] += l.getBreakdownHours().doubleValue();
    }
    List<UtilizationRow> rows = new ArrayList<>(agg.size());
    for (Map.Entry<UUID, double[]> e : agg.entrySet()) {
      Resource r = resourcesById.get(e.getKey());
      double[] v = e.getValue();
      double total = v[0] + v[1] + v[2];
      double pct = total > 0 ? v[0] / total : 0d;
      double maPct = total > 0 ? (v[0] + v[1]) / total : 0d;
      rows.add(new UtilizationRow(
          e.getKey(),
          r != null ? r.getCode() : "?",
          r != null ? r.getName() : "Unknown",
          round2(v[0]), round2(v[1]), round2(v[2]),
          round4(pct), round4(maPct)));
    }
    rows.sort(Comparator.comparingDouble(UtilizationRow::utilizationPct).reversed());
    return rows;
  }

  private double computeMechanicalAvailabilityHeadline(List<UtilizationRow> rows) {
    List<UtilizationRow> usable = rows.stream()
        .filter(r -> r.operatingHours() + r.idleHours() + r.breakdownHours() > 0d)
        .toList();
    if (usable.isEmpty()) return 0d;
    return usable.stream().mapToDouble(UtilizationRow::mechanicalAvailabilityPct).average().orElse(0d);
  }

  private double computeEpiHeadline(List<AvailabilityPerformanceRow> rows) {
    List<AvailabilityPerformanceRow> usable = rows.stream()
        .filter(r -> r.performance() > 0d)
        .toList();
    if (usable.isEmpty()) return 0d;
    return usable.stream().mapToDouble(AvailabilityPerformanceRow::performance).average().orElse(0d);
  }

  // ---------- Idle-time alerts ----------

  private List<IdleAlertRow> computeIdleAlerts(
      List<EnrichedDprEquipment> enriched,
      Map<UUID, Resource> resourcesById) {
    LocalDate latestDate = enriched.stream()
        .map(EnrichedDprEquipment::logDate)
        .filter(java.util.Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);
    if (latestDate == null) return List.of();
    return enriched.stream()
        .filter(e -> latestDate.equals(e.logDate()))
        .filter(e -> e.row().getIdleHours() != null
            && e.row().getIdleHours().doubleValue() > DEFAULT_IDLE_THRESHOLD_HOURS)
        .filter(e -> e.row().getResourceId() != null)
        .sorted(Comparator.comparingDouble(
            (EnrichedDprEquipment x) -> x.row().getIdleHours().doubleValue()).reversed())
        .map(e -> {
          Resource r = resourcesById.get(e.row().getResourceId());
          return new IdleAlertRow(
              e.row().getResourceId(),
              r != null ? r.getCode() : "?",
              r != null ? r.getName() : "Unknown",
              e.logDate(),
              round2(e.row().getIdleHours().doubleValue()));
        })
        .toList();
  }

  // ---------- Fuel per output ----------

  private List<FuelPerOutputRow> computeFuelPerOutput(
      List<EnrichedDprEquipment> enriched,
      Map<UUID, Resource> resourcesById) {
    Map<UUID, double[]> agg = new HashMap<>(); // [fuel, qty]
    for (EnrichedDprEquipment e : enriched) {
      DprEquipment l = e.row();
      if (l.getResourceId() == null) continue;
      if (l.getFuelLitres() == null || l.getFuelLitres().signum() <= 0) continue;
      double[] acc = agg.computeIfAbsent(l.getResourceId(), k -> new double[2]);
      acc[0] += l.getFuelLitres().doubleValue();
      acc[1] += e.attributedQty();
    }
    List<FuelPerOutputRow> rows = new ArrayList<>(agg.size());
    for (Map.Entry<UUID, double[]> e : agg.entrySet()) {
      Resource r = resourcesById.get(e.getKey());
      double fuel = e.getValue()[0];
      double qty = e.getValue()[1];
      double fpo = qty > 0 ? fuel / qty : 0d;
      rows.add(new FuelPerOutputRow(
          e.getKey(),
          r != null ? r.getCode() : "?",
          r != null ? r.getName() : "Unknown",
          round2(fuel), round3(qty), round4(fpo)));
    }
    rows.sort(Comparator.comparingDouble(FuelPerOutputRow::fuelPerOutput).reversed());
    return rows;
  }

  // ---------- Availability + Performance (KPI 6.1 EPI input) ----------

  private List<AvailabilityPerformanceRow> computeAvailabilityPerformance(
      List<EnrichedDprEquipment> enriched,
      Map<UUID, Resource> resourcesById,
      Map<UUID, ResourceEquipmentDetails> detailsById) {
    Map<UUID, double[]> agg = new HashMap<>(); // [op, idle, breakdown, qty]
    Map<UUID, Set<LocalDate>> daysSeen = new HashMap<>();
    for (EnrichedDprEquipment e : enriched) {
      DprEquipment l = e.row();
      if (l.getResourceId() == null) continue;
      double[] acc = agg.computeIfAbsent(l.getResourceId(), k -> new double[4]);
      if (l.getWorkingHours() != null) acc[0] += l.getWorkingHours().doubleValue();
      if (l.getIdleHours() != null) acc[1] += l.getIdleHours().doubleValue();
      if (l.getBreakdownHours() != null) acc[2] += l.getBreakdownHours().doubleValue();
      acc[3] += e.attributedQty();
      if (e.logDate() != null) {
        daysSeen.computeIfAbsent(l.getResourceId(), k -> new java.util.HashSet<>()).add(e.logDate());
      }
    }

    List<AvailabilityPerformanceRow> rows = new ArrayList<>(agg.size());
    for (Map.Entry<UUID, double[]> e : agg.entrySet()) {
      Resource r = resourcesById.get(e.getKey());
      ResourceEquipmentDetails d = detailsById.get(e.getKey());
      double[] v = e.getValue();
      double total = v[0] + v[1] + v[2];
      double availability = total > 0 ? (v[0] + v[1]) / total : 0d;

      double standardPerDay = d != null && d.getStandardOutputPerDay() != null
          ? d.getStandardOutputPerDay().doubleValue() : 0d;
      double daysCount = daysSeen.getOrDefault(e.getKey(), Set.of()).size();
      double actualPerDay = daysCount > 0 ? v[3] / daysCount : 0d;
      double performance = standardPerDay > 0 ? actualPerDay / standardPerDay : 0d;

      rows.add(new AvailabilityPerformanceRow(
          e.getKey(),
          r != null ? r.getCode() : "?",
          r != null ? r.getName() : "Unknown",
          round4(availability),
          round4(performance)));
    }
    rows.sort(Comparator.comparing(AvailabilityPerformanceRow::resourceCode));
    return rows;
  }

  // ---------- Owned vs rented ----------

  private List<OwnedRentedSlice> computeOwnedVsRented(
      List<EnrichedDprEquipment> enriched,
      Map<UUID, Resource> resourcesById,
      Map<UUID, ResourceEquipmentDetails> detailsById) {
    Map<String, double[]> bucket = new HashMap<>(); // [hours, cost]
    for (EnrichedDprEquipment e : enriched) {
      DprEquipment l = e.row();
      double hours = l.getWorkingHours() != null ? l.getWorkingHours().doubleValue() : 0d;
      // Prefer the line_cost the supervisor entered on the DPR row; fall back to
      // hours × Resource.costPerUnit when the DPR row didn't carry one.
      double cost;
      if (l.getLineCost() != null && l.getLineCost().signum() > 0) {
        cost = l.getLineCost().doubleValue();
      } else {
        Resource r = l.getResourceId() != null ? resourcesById.get(l.getResourceId()) : null;
        BigDecimal cpu = r != null ? r.getCostPerUnit() : null;
        cost = (cpu != null ? cpu.doubleValue() : 0d) * hours;
      }
      ResourceEquipmentDetails d = l.getResourceId() != null ? detailsById.get(l.getResourceId()) : null;
      ResourceOwnership ownership = d != null ? d.getOwnershipType() : null;
      String key = ownership != null ? ownership.name() : "UNKNOWN";
      double[] acc = bucket.computeIfAbsent(key, k -> new double[2]);
      acc[0] += hours;
      acc[1] += cost;
    }
    List<OwnedRentedSlice> slices = new ArrayList<>(bucket.size());
    for (Map.Entry<String, double[]> e : bucket.entrySet()) {
      slices.add(new OwnedRentedSlice(e.getKey(), round2(e.getValue()[0]), round2(e.getValue()[1])));
    }
    slices.sort(Comparator.comparingDouble(OwnedRentedSlice::cost).reversed());
    return slices;
  }

  // ---------- Service-due ----------

  private List<ServiceDueRow> computeServiceDue(
      UUID projectId,
      Map<UUID, Resource> resourcesById,
      Map<UUID, ResourceEquipmentDetails> detailsById) {
    LocalDate today = LocalDate.now();
    LocalDate cutoff = today.plusDays(DEFAULT_SERVICE_LOOKAHEAD_DAYS);
    List<ServiceDueRow> rows = new ArrayList<>();
    for (Map.Entry<UUID, ResourceEquipmentDetails> e : detailsById.entrySet()) {
      ResourceEquipmentDetails d = e.getValue();
      if (d.getNextServiceDate() == null) continue;
      if (d.getNextServiceDate().isAfter(cutoff)) continue;
      Resource r = resourcesById.get(e.getKey());
      if (r == null || r.getResourceType() == null
          || !EQUIPMENT_TYPE_CODE.equalsIgnoreCase(r.getResourceType().getCode())) continue;
      long days = today.until(d.getNextServiceDate()).getDays();
      rows.add(new ServiceDueRow(
          e.getKey(),
          r.getCode(),
          r.getName(),
          d.getNextServiceDate(),
          days));
    }
    rows.sort(Comparator.comparingLong(ServiceDueRow::daysUntilService));
    log.debug("Equipment service-due rows for project {}: {}", projectId, rows.size());
    return rows;
  }

  // ---------- Empty response ----------

  private EquipmentKpiResponse emptyResponse(UUID projectId, LocalDate from, LocalDate to) {
    return new EquipmentKpiResponse(
        projectId, from, to,
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        0d, 0d);
  }

  // ---------- Misc ----------

  private static double round2(double v) {
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private static double round3(double v) {
    return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
  }

  private static double round4(double v) {
    return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
  }
}
