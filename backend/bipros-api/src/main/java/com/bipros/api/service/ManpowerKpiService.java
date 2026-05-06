package com.bipros.api.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.manpower.ManpowerAttendance;
import com.bipros.resource.domain.model.manpower.ManpowerFinancials;
import com.bipros.resource.domain.model.enums.SalaryType;
import com.bipros.resource.domain.repository.ManpowerAttendanceRepository;
import com.bipros.resource.domain.repository.ManpowerFinancialsRepository;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
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
 * Aggregator KPI service for manpower metrics. Lives in {@code bipros-api} (the only module
 * that depends on every domain module) so it can read both DPR/DAR (in {@code bipros-project})
 * and Manpower* + ProductivityNorm + Resource (in {@code bipros-resource}) without inverting
 * the domain dependency graph.
 *
 * <p>All metrics are computed in-memory from pre-fetched data so dashboard response time is
 * single-query bound. Cost normalisation uses pre-answer #5 from the source plan: salaries
 * are converted to a per-day rate via PERMANENT/30, CONTRACT/26, DAILY_WAGE direct.
 *
 * <p>Methods that require StorePeriodPerformance (labour CPI, overtime ROI) are deferred to
 * a follow-up phase — the current data shape is enough for the four headline cards on the
 * Field, Operational, and Executive dashboards.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManpowerKpiService {

  private static final String LABOR_TYPE_CODE = "LABOR";

  private final DailyActivityResourceOutputRepository darRepository;
  private final BoqItemRepository boqItemRepository;
  private final ActivityRepository activityRepository;
  private final ResourceRepository resourceRepository;
  private final ManpowerAttendanceRepository attendanceRepository;
  private final ManpowerFinancialsRepository financialsRepository;
  private final ProductivityNormRepository productivityNormRepository;

  // ---------- Response records (shape matches what the dashboards consume) ----------

  public record ManpowerKpiResponse(
      UUID projectId,
      LocalDate from,
      LocalDate to,
      WorkforceUtilization workforceUtilization,
      List<ProductivityFactorRow> productivityFactor,
      List<LabourCostPerUnitRow> labourCostPerUnit,
      List<CrewOutputRow> crewOutput
  ) {}

  public record WorkforceUtilization(
      double actualHours,
      double availableHours,
      double utilizationPct,
      int laborResourceCount
  ) {}

  public record ProductivityFactorRow(
      UUID activityId,
      String activityName,
      double actualOutputPerManPerDay,
      double normOutputPerManPerDay,
      double factor
  ) {}

  public record LabourCostPerUnitRow(
      UUID boqItemId,
      String itemNo,
      String description,
      String unit,
      double labourCost,
      double qtyExecuted,
      double costPerUnit
  ) {}

  public record CrewOutputRow(
      UUID activityId,
      String activityName,
      Integer crewSize,
      double actualOutputPerDay,
      double normOutputPerDay,
      double deviationPct
  ) {}

  // ---------- Public API ----------

  @Transactional(readOnly = true)
  public ManpowerKpiResponse compute(UUID projectId, LocalDate from, LocalDate to) {
    List<DailyActivityResourceOutput> dar = darRepository
        .findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(projectId, from, to);

    Set<UUID> resourceIds = dar.stream()
        .map(DailyActivityResourceOutput::getResourceId)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());

    Map<UUID, Resource> resourcesById = resourceRepository.findAllById(resourceIds).stream()
        .collect(Collectors.toMap(Resource::getId, r -> r, (a, b) -> a));
    Set<UUID> labourResourceIds = resourcesById.values().stream()
        .filter(r -> r.getResourceType() != null && LABOR_TYPE_CODE.equalsIgnoreCase(r.getResourceType().getCode()))
        .map(Resource::getId)
        .collect(Collectors.toSet());

    List<DailyActivityResourceOutput> labourDar = dar.stream()
        .filter(d -> d.getResourceId() != null && labourResourceIds.contains(d.getResourceId()))
        .toList();

    WorkforceUtilization workforce = computeWorkforceUtilization(labourDar, labourResourceIds, from, to);
    List<ProductivityFactorRow> productivity = computeProductivityFactor(labourDar);
    List<LabourCostPerUnitRow> labourCost = computeLabourCostPerUnit(projectId, labourDar, resourcesById);
    List<CrewOutputRow> crews = computeCrewOutput(labourDar);

    return new ManpowerKpiResponse(projectId, from, to, workforce, productivity, labourCost, crews);
  }

  // ---------- Workforce utilisation ----------

  private WorkforceUtilization computeWorkforceUtilization(
      List<DailyActivityResourceOutput> labourDar,
      Set<UUID> labourResourceIds,
      LocalDate from,
      LocalDate to) {
    double actualHours = labourDar.stream()
        .map(DailyActivityResourceOutput::getHoursWorked)
        .filter(java.util.Objects::nonNull)
        .mapToDouble(Double::doubleValue)
        .sum();

    long workingDays = Math.max(1L, from.until(to).getDays() + 1L);
    Map<UUID, ManpowerAttendance> attendanceById = attendanceRepository
        .findAllById(labourResourceIds).stream()
        .collect(Collectors.toMap(ManpowerAttendance::getResourceId, a -> a, (a, b) -> a));

    double availableHours = 0d;
    for (UUID rid : labourResourceIds) {
      ManpowerAttendance a = attendanceById.get(rid);
      double hpd = a != null && a.getWorkingHoursPerDay() != null
          ? a.getWorkingHoursPerDay().doubleValue()
          : 8d; // Sensible default when attendance master row is missing.
      availableHours += hpd * workingDays;
    }

    double pct = availableHours > 0 ? actualHours / availableHours : 0d;
    return new WorkforceUtilization(
        roundHours(actualHours),
        roundHours(availableHours),
        round4(pct),
        labourResourceIds.size());
  }

  // ---------- Productivity factor by activity ----------

  private List<ProductivityFactorRow> computeProductivityFactor(List<DailyActivityResourceOutput> labourDar) {
    // Aggregate per activity.
    Map<UUID, double[]> byActivity = new HashMap<>(); // [qty, hours]
    for (DailyActivityResourceOutput d : labourDar) {
      if (d.getActivityId() == null) continue;
      double[] acc = byActivity.computeIfAbsent(d.getActivityId(), k -> new double[2]);
      if (d.getQtyExecuted() != null) acc[0] += d.getQtyExecuted().doubleValue();
      if (d.getHoursWorked() != null) acc[1] += d.getHoursWorked();
    }
    if (byActivity.isEmpty()) return List.of();

    Map<UUID, Activity> activitiesById = activityRepository.findAllById(byActivity.keySet()).stream()
        .collect(Collectors.toMap(Activity::getId, a -> a, (a, b) -> a));

    List<ProductivityFactorRow> rows = new ArrayList<>(byActivity.size());
    for (Map.Entry<UUID, double[]> e : byActivity.entrySet()) {
      Activity activity = activitiesById.get(e.getKey());
      double qty = e.getValue()[0];
      double hours = e.getValue()[1];
      double daysEquivalent = hours > 0 ? hours / 8d : 0d;
      double actualPerManPerDay = daysEquivalent > 0 ? qty / daysEquivalent : 0d;

      double norm = lookupProductivityNormPerManPerDay(activity);
      double factor = norm > 0 ? actualPerManPerDay / norm : 0d;

      rows.add(new ProductivityFactorRow(
          e.getKey(),
          activity != null ? activity.getName() : "Unknown",
          round4(actualPerManPerDay),
          round4(norm),
          round4(factor)));
    }
    rows.sort(Comparator.comparingDouble(ProductivityFactorRow::factor));
    return rows;
  }

  private double lookupProductivityNormPerManPerDay(Activity activity) {
    if (activity == null || activity.getName() == null) return 0d;
    List<ProductivityNorm> matches = productivityNormRepository.findByActivityNameIgnoreCase(activity.getName());
    return matches.stream()
        .map(ProductivityNorm::getOutputPerManPerDay)
        .filter(java.util.Objects::nonNull)
        .map(BigDecimal::doubleValue)
        .findFirst()
        .orElse(0d);
  }

  // ---------- Labour cost per unit (by BoqItem) ----------

  private List<LabourCostPerUnitRow> computeLabourCostPerUnit(
      UUID projectId,
      List<DailyActivityResourceOutput> labourDar,
      Map<UUID, Resource> resourcesById) {

    if (labourDar.isEmpty()) return List.of();

    Map<UUID, ManpowerFinancials> financialsById = financialsRepository.findAllById(resourcesById.keySet()).stream()
        .collect(Collectors.toMap(ManpowerFinancials::getResourceId, f -> f, (a, b) -> a));

    // Map activityId → labourCost (Σ hours × hourlyRate-equivalent across labour resources for that activity)
    Map<UUID, Double> labourCostByActivity = new HashMap<>();
    for (DailyActivityResourceOutput d : labourDar) {
      if (d.getActivityId() == null || d.getResourceId() == null) continue;
      double hours = d.getHoursWorked() != null ? d.getHoursWorked() : 0d;
      if (hours <= 0d) continue;
      double hourlyRate = effectiveHourlyRate(financialsById.get(d.getResourceId()));
      labourCostByActivity.merge(d.getActivityId(), hours * hourlyRate, Double::sum);
    }

    // Map activityId → activityName for BOQ matching by name (fallback when activity_code is BOQ-keyed).
    Map<UUID, Activity> activitiesById = activityRepository.findAllById(labourCostByActivity.keySet()).stream()
        .collect(Collectors.toMap(Activity::getId, a -> a, (a, b) -> a));

    // BOQ rows for the project — match by itemNo present in the activity name (prefix match) OR by exact name.
    List<BoqItem> boq = boqItemRepository.findByProjectIdOrderByItemNoAsc(projectId);
    Map<UUID, double[]> aggByBoq = new HashMap<>(); // [labourCost, qtyExecuted]
    for (Map.Entry<UUID, Double> e : labourCostByActivity.entrySet()) {
      Activity a = activitiesById.get(e.getKey());
      if (a == null) continue;
      BoqItem match = matchBoqByActivityName(boq, a.getName());
      if (match == null) continue;
      double[] acc = aggByBoq.computeIfAbsent(match.getId(), k -> new double[2]);
      acc[0] += e.getValue();
    }
    // Qty executed: sum DAR qty whose activity matched a BOQ.
    for (DailyActivityResourceOutput d : labourDar) {
      Activity a = activitiesById.get(d.getActivityId());
      if (a == null) continue;
      BoqItem match = matchBoqByActivityName(boq, a.getName());
      if (match == null) continue;
      double[] acc = aggByBoq.computeIfAbsent(match.getId(), k -> new double[2]);
      if (d.getQtyExecuted() != null) acc[1] += d.getQtyExecuted().doubleValue();
    }

    List<LabourCostPerUnitRow> rows = new ArrayList<>(aggByBoq.size());
    for (Map.Entry<UUID, double[]> e : aggByBoq.entrySet()) {
      BoqItem item = boq.stream().filter(b -> b.getId().equals(e.getKey())).findFirst().orElse(null);
      if (item == null) continue;
      double labourCost = e.getValue()[0];
      double qty = e.getValue()[1];
      double cpu = qty > 0 ? labourCost / qty : 0d;
      rows.add(new LabourCostPerUnitRow(
          item.getId(),
          item.getItemNo(),
          item.getDescription(),
          item.getUnit(),
          round2(labourCost),
          round3(qty),
          round2(cpu)));
    }
    rows.sort(Comparator.comparingDouble(LabourCostPerUnitRow::costPerUnit).reversed());
    return rows;
  }

  /**
   * Pre-answer #5 normalisation: PERMANENT salary → /30/8 hourly, CONTRACT → /26/8 hourly,
   * DAILY_WAGE → daily/8 hourly, HOURLY → hourlyRate directly.
   */
  private static double effectiveHourlyRate(ManpowerFinancials f) {
    if (f == null) return 0d;
    if (f.getHourlyRate() != null && f.getHourlyRate().signum() > 0) {
      return f.getHourlyRate().doubleValue();
    }
    if (f.getBaseSalary() == null) return 0d;
    SalaryType t = f.getSalaryType();
    if (t == null) return 0d;
    return switch (t) {
      case MONTHLY -> f.getBaseSalary().doubleValue() / 30d / 8d;
      case DAILY -> f.getBaseSalary().doubleValue() / 8d;
      case HOURLY -> f.getBaseSalary().doubleValue();
    };
  }

  /**
   * Best-effort BOQ ↔ activity match by name. Uses the same exact + substring strategy as
   * {@code DailyCostReportService} so the numbers reconcile across pages. Returns the first
   * match, or null.
   */
  private static BoqItem matchBoqByActivityName(List<BoqItem> boq, String activityName) {
    if (activityName == null || activityName.isBlank()) return null;
    String needle = activityName.toLowerCase();
    for (BoqItem b : boq) {
      if (b.getItemNo() != null && needle.contains(b.getItemNo().toLowerCase())) return b;
    }
    for (BoqItem b : boq) {
      if (b.getDescription() != null && needle.contains(b.getDescription().toLowerCase())) return b;
    }
    return null;
  }

  // ---------- Crew output rows ----------

  private List<CrewOutputRow> computeCrewOutput(List<DailyActivityResourceOutput> labourDar) {
    Map<UUID, double[]> byActivity = new HashMap<>(); // [qty, daysEquivalent]
    for (DailyActivityResourceOutput d : labourDar) {
      if (d.getActivityId() == null) continue;
      double[] acc = byActivity.computeIfAbsent(d.getActivityId(), k -> new double[2]);
      if (d.getQtyExecuted() != null) acc[0] += d.getQtyExecuted().doubleValue();
      double hours = d.getHoursWorked() != null ? d.getHoursWorked() : 0d;
      acc[1] += hours / 8d;
    }
    Map<UUID, Activity> activitiesById = activityRepository.findAllById(byActivity.keySet()).stream()
        .collect(Collectors.toMap(Activity::getId, a -> a, (a, b) -> a));

    List<CrewOutputRow> rows = new ArrayList<>(byActivity.size());
    for (Map.Entry<UUID, double[]> e : byActivity.entrySet()) {
      Activity a = activitiesById.get(e.getKey());
      double qty = e.getValue()[0];
      double daysEq = e.getValue()[1];
      double actualPerDay = daysEq > 0 ? qty / daysEq : 0d;
      ProductivityNorm norm = lookupProductivityNormForActivity(a);
      Integer crewSize = norm != null ? norm.getCrewSize() : null;
      double normPerDay = norm != null && norm.getOutputPerDay() != null
          ? norm.getOutputPerDay().doubleValue() : 0d;
      double dev = normPerDay > 0 ? (actualPerDay - normPerDay) / normPerDay : 0d;

      rows.add(new CrewOutputRow(
          e.getKey(),
          a != null ? a.getName() : "Unknown",
          crewSize,
          round4(actualPerDay),
          round4(normPerDay),
          round4(dev)));
    }
    rows.sort(Comparator.comparingDouble(CrewOutputRow::deviationPct));
    return rows;
  }

  private ProductivityNorm lookupProductivityNormForActivity(Activity activity) {
    if (activity == null || activity.getName() == null) return null;
    return productivityNormRepository.findByActivityNameIgnoreCase(activity.getName())
        .stream().findFirst().orElse(null);
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

  private static double roundHours(double v) {
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  // Imports may flag ResourceType unused at compile time but we need the import for the typed lambda.
  @SuppressWarnings("unused")
  private void __referenceForImportSanity(ResourceType t) {}
}
