package com.bipros.api.service;

import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.resource.domain.model.EquipmentLog;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceEquipmentDetails;
import com.bipros.resource.domain.model.ResourceOwnership;
import com.bipros.resource.domain.repository.EquipmentLogRepository;
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
 * Aggregator KPI service for equipment metrics. Lives in {@code bipros-api} for the same
 * cross-module reason as {@link ManpowerKpiService}.
 *
 * <p>Per pre-answer #6, owned-vs-rented cost uses {@code Resource.costPerUnit} for both
 * ownership types — the dashboard tooltip surfaces the apples-to-apples caveat.
 *
 * <p>OEE composite (Availability × Performance × Quality) is deliberately NOT exposed: the
 * Quality factor needs the QC module which is deferred. Availability and Performance are
 * exposed separately so they can be charted without implying a fake OEE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EquipmentKpiService {

  private static final String EQUIPMENT_TYPE_CODE = "EQUIPMENT";
  private static final double DEFAULT_IDLE_THRESHOLD_HOURS = 2d;
  private static final int DEFAULT_SERVICE_LOOKAHEAD_DAYS = 7;

  private final EquipmentLogRepository equipmentLogRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceEquipmentDetailsRepository equipmentDetailsRepository;
  private final DailyActivityResourceOutputRepository darRepository;

  // ---------- Response shapes ----------

  public record EquipmentKpiResponse(
      UUID projectId,
      LocalDate from,
      LocalDate to,
      List<UtilizationRow> utilization,
      List<IdleAlertRow> idleAlerts,
      List<FuelPerOutputRow> fuelPerOutput,
      List<AvailabilityPerformanceRow> availabilityPerformance,
      List<OwnedRentedSlice> ownedVsRented,
      List<ServiceDueRow> serviceDue
  ) {}

  public record UtilizationRow(
      UUID resourceId,
      String resourceCode,
      String resourceName,
      double operatingHours,
      double idleHours,
      double breakdownHours,
      double utilizationPct
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

  // ---------- Public API ----------

  @Transactional(readOnly = true)
  public EquipmentKpiResponse compute(UUID projectId, LocalDate from, LocalDate to) {
    List<EquipmentLog> logs = equipmentLogRepository.findByProjectIdAndLogDateBetween(projectId, from, to);
    Set<UUID> resourceIds = logs.stream().map(EquipmentLog::getResourceId).collect(Collectors.toSet());
    Map<UUID, Resource> resourcesById = resourceRepository.findAllById(resourceIds).stream()
        .collect(Collectors.toMap(Resource::getId, r -> r, (a, b) -> a));
    Map<UUID, ResourceEquipmentDetails> detailsById =
        equipmentDetailsRepository.findAllById(resourceIds).stream()
            .collect(Collectors.toMap(ResourceEquipmentDetails::getResourceId, d -> d, (a, b) -> a));

    List<UtilizationRow> utilization = computeUtilization(logs, resourcesById);
    List<IdleAlertRow> idleAlerts = computeIdleAlerts(logs, resourcesById);
    List<FuelPerOutputRow> fuelPerOutput = computeFuelPerOutput(projectId, logs, resourcesById, from, to);
    List<AvailabilityPerformanceRow> availPerf = computeAvailabilityPerformance(
        projectId, logs, resourcesById, detailsById, from, to);
    List<OwnedRentedSlice> ownedVsRented = computeOwnedVsRented(logs, resourcesById, detailsById);
    List<ServiceDueRow> serviceDue = computeServiceDue(projectId, resourcesById, detailsById);

    return new EquipmentKpiResponse(
        projectId, from, to, utilization, idleAlerts, fuelPerOutput,
        availPerf, ownedVsRented, serviceDue);
  }

  // ---------- Utilisation ----------

  private List<UtilizationRow> computeUtilization(List<EquipmentLog> logs, Map<UUID, Resource> resourcesById) {
    Map<UUID, double[]> agg = new HashMap<>(); // [op, idle, breakdown]
    for (EquipmentLog l : logs) {
      double[] acc = agg.computeIfAbsent(l.getResourceId(), k -> new double[3]);
      if (l.getOperatingHours() != null) acc[0] += l.getOperatingHours();
      if (l.getIdleHours() != null) acc[1] += l.getIdleHours();
      if (l.getBreakdownHours() != null) acc[2] += l.getBreakdownHours();
    }
    List<UtilizationRow> rows = new ArrayList<>(agg.size());
    for (Map.Entry<UUID, double[]> e : agg.entrySet()) {
      Resource r = resourcesById.get(e.getKey());
      double[] v = e.getValue();
      double total = v[0] + v[1] + v[2];
      double pct = total > 0 ? v[0] / total : 0d;
      rows.add(new UtilizationRow(
          e.getKey(),
          r != null ? r.getCode() : "?",
          r != null ? r.getName() : "Unknown",
          round2(v[0]), round2(v[1]), round2(v[2]), round4(pct)));
    }
    rows.sort(Comparator.comparingDouble(UtilizationRow::utilizationPct).reversed());
    return rows;
  }

  // ---------- Idle-time alerts ----------

  private List<IdleAlertRow> computeIdleAlerts(List<EquipmentLog> logs, Map<UUID, Resource> resourcesById) {
    LocalDate latestDate = logs.stream()
        .map(EquipmentLog::getLogDate)
        .max(Comparator.naturalOrder())
        .orElse(null);
    if (latestDate == null) return List.of();
    return logs.stream()
        .filter(l -> latestDate.equals(l.getLogDate()))
        .filter(l -> l.getIdleHours() != null && l.getIdleHours() > DEFAULT_IDLE_THRESHOLD_HOURS)
        .sorted(Comparator.comparingDouble(EquipmentLog::getIdleHours).reversed())
        .map(l -> {
          Resource r = resourcesById.get(l.getResourceId());
          return new IdleAlertRow(
              l.getResourceId(),
              r != null ? r.getCode() : "?",
              r != null ? r.getName() : "Unknown",
              l.getLogDate(),
              round2(l.getIdleHours()));
        })
        .toList();
  }

  // ---------- Fuel per output ----------

  private List<FuelPerOutputRow> computeFuelPerOutput(
      UUID projectId,
      List<EquipmentLog> logs,
      Map<UUID, Resource> resourcesById,
      LocalDate from,
      LocalDate to) {
    Map<UUID, Double> fuelByResource = new HashMap<>();
    for (EquipmentLog l : logs) {
      if (l.getFuelConsumed() == null) continue;
      fuelByResource.merge(l.getResourceId(), l.getFuelConsumed(), Double::sum);
    }
    if (fuelByResource.isEmpty()) return List.of();

    // Equipment qty executed comes from DailyActivityResourceOutput where the resource
    // is an equipment resource. Sum across activities for the same period.
    List<DailyActivityResourceOutput> dar = darRepository
        .findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(projectId, from, to);
    Map<UUID, Double> qtyByResource = new HashMap<>();
    for (DailyActivityResourceOutput d : dar) {
      if (d.getResourceId() == null || !fuelByResource.containsKey(d.getResourceId())) continue;
      if (d.getQtyExecuted() != null) {
        qtyByResource.merge(d.getResourceId(), d.getQtyExecuted().doubleValue(), Double::sum);
      }
    }

    List<FuelPerOutputRow> rows = new ArrayList<>(fuelByResource.size());
    for (Map.Entry<UUID, Double> e : fuelByResource.entrySet()) {
      Resource r = resourcesById.get(e.getKey());
      double fuel = e.getValue();
      double qty = qtyByResource.getOrDefault(e.getKey(), 0d);
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

  // ---------- Availability + Performance ----------

  private List<AvailabilityPerformanceRow> computeAvailabilityPerformance(
      UUID projectId,
      List<EquipmentLog> logs,
      Map<UUID, Resource> resourcesById,
      Map<UUID, ResourceEquipmentDetails> detailsById,
      LocalDate from,
      LocalDate to) {
    Map<UUID, double[]> agg = new HashMap<>(); // [op, idle, breakdown]
    Map<UUID, Long> daysSeen = new HashMap<>();
    for (EquipmentLog l : logs) {
      double[] acc = agg.computeIfAbsent(l.getResourceId(), k -> new double[3]);
      if (l.getOperatingHours() != null) acc[0] += l.getOperatingHours();
      if (l.getIdleHours() != null) acc[1] += l.getIdleHours();
      if (l.getBreakdownHours() != null) acc[2] += l.getBreakdownHours();
      daysSeen.merge(l.getResourceId(), 1L, Long::sum);
    }

    Map<UUID, Double> qtyByResource = new HashMap<>();
    List<DailyActivityResourceOutput> dar = darRepository
        .findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(projectId, from, to);
    for (DailyActivityResourceOutput d : dar) {
      if (d.getResourceId() == null) continue;
      if (d.getQtyExecuted() != null) {
        qtyByResource.merge(d.getResourceId(), d.getQtyExecuted().doubleValue(), Double::sum);
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
      double daysCount = daysSeen.getOrDefault(e.getKey(), 0L).doubleValue();
      double actualPerDay = daysCount > 0 ? qtyByResource.getOrDefault(e.getKey(), 0d) / daysCount : 0d;
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
      List<EquipmentLog> logs,
      Map<UUID, Resource> resourcesById,
      Map<UUID, ResourceEquipmentDetails> detailsById) {
    Map<String, double[]> bucket = new HashMap<>(); // [hours, cost]
    for (EquipmentLog l : logs) {
      double hours = l.getOperatingHours() != null ? l.getOperatingHours() : 0d;
      ResourceEquipmentDetails d = detailsById.get(l.getResourceId());
      ResourceOwnership ownership = d != null ? d.getOwnershipType() : null;
      Resource r = resourcesById.get(l.getResourceId());
      BigDecimal cpu = r != null ? r.getCostPerUnit() : null;
      double cost = (cpu != null ? cpu.doubleValue() : 0d) * hours;
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
    // Defensive: callers may not iterate the project explicitly — projectId is logged for trace.
    log.debug("Equipment service-due rows for project {}: {}", projectId, rows.size());
    return rows;
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
