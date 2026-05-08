package com.bipros.api.service;

import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprMaterial;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprMaterialRepository;
import com.bipros.resource.domain.model.MaterialConsumptionLog;
import com.bipros.resource.domain.model.MaterialIssue;
import com.bipros.resource.domain.repository.MaterialConsumptionLogRepository;
import com.bipros.resource.domain.repository.MaterialIssueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * NH-48 KPI Framework Phase 1 — Material KPIs from existing entities only.
 *
 * <p>Inputs come from three sources:
 * <ul>
 *   <li>{@code material_issues} — what left the store (qty + wastage)</li>
 *   <li>{@code material_consumption_logs} — opening/closing stock + consumed/wastage% per day</li>
 *   <li>{@code dpr_material} — what each DPR row reports as consumed (with actual unit_rate)</li>
 * </ul>
 *
 * <p>KPIs requiring "planned qty" (8.4 Consumption Efficiency, 9.2 Usage Variance) need a BOQ
 * baseline that isn't stored per material. They surface as {@code null} in Phase 1 — the UI
 * shows a "—" placeholder rather than fabricating a number. Full coverage of those KPIs is
 * tracked in Phase 2 (material_batch_designs schema addition).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialKpiService {

  private final MaterialIssueRepository issueRepository;
  private final MaterialConsumptionLogRepository consumptionLogRepository;
  private final DailyProgressReportRepository dprRepository;
  private final DprMaterialRepository dprMaterialRepository;

  // ---------- Response shapes ----------

  public record MaterialKpiResponse(
      UUID projectId,
      LocalDate from,
      LocalDate to,
      double issuedQty,
      double consumedQty,
      double wastageQty,
      double materialUtilizationPct,
      double wastagePct,
      double reconciliationBalance,
      Double materialPriceVariance,
      Double materialUsageVariance,
      Double totalMaterialCostVariance,
      List<MaterialBreakdownRow> byMaterial
  ) {}

  public record MaterialBreakdownRow(
      String materialName,
      double issuedQty,
      double consumedQty,
      double wastageQty,
      double utilizationPct,
      double avgUnitRate
  ) {}

  // ---------- Public API ----------

  @Transactional(readOnly = true)
  public MaterialKpiResponse compute(UUID projectId, LocalDate from, LocalDate to) {
    List<MaterialIssue> issues = issueRepository.findByProjectIdAndIssueDateBetween(projectId, from, to);
    List<MaterialConsumptionLog> logs = consumptionLogRepository
        .findByProjectIdAndLogDateBetweenOrderByLogDateAscIdAsc(projectId, from, to);
    List<DprMaterial> dprMaterials = fetchDprMaterials(projectId, from, to);

    double issuedQty = issues.stream()
        .map(MaterialIssue::getQuantity)
        .filter(java.util.Objects::nonNull)
        .mapToDouble(BigDecimal::doubleValue)
        .sum();
    double issueWastage = issues.stream()
        .map(MaterialIssue::getWastageQuantity)
        .filter(java.util.Objects::nonNull)
        .mapToDouble(BigDecimal::doubleValue)
        .sum();
    double consumedQty = logs.stream()
        .map(MaterialConsumptionLog::getConsumed)
        .filter(java.util.Objects::nonNull)
        .mapToDouble(BigDecimal::doubleValue)
        .sum();

    // Wastage = issued − consumed (clamped to ≥ 0). Returns are not modelled today, so
    // negative values would imply a data-quality issue rather than legitimate over-consumption.
    double wastageQty = Math.max(0d, issuedQty - consumedQty);
    if (wastageQty == 0d && issueWastage > 0d) wastageQty = issueWastage;

    double utilizationPct = issuedQty > 0 ? consumedQty / issuedQty : 0d;
    double wastagePct = issuedQty > 0 ? wastageQty / issuedQty : 0d;
    double reconBalance = issuedQty - consumedQty - wastageQty;

    // KPI 9.x — variance from DPR materials. Std rate is unavailable in Phase 1 (no BOQ ↔
    // material map), so price / usage variances are surfaced as null. Average actual rate is
    // still useful in the per-material breakdown.
    Double priceVariance = null;
    Double usageVariance = null;
    Double totalVariance = null;

    List<MaterialBreakdownRow> breakdown = computeBreakdown(issues, logs, dprMaterials);

    return new MaterialKpiResponse(
        projectId,
        from,
        to,
        round3(issuedQty),
        round3(consumedQty),
        round3(wastageQty),
        round4(utilizationPct),
        round4(wastagePct),
        round3(reconBalance),
        priceVariance,
        usageVariance,
        totalVariance,
        breakdown);
  }

  private List<DprMaterial> fetchDprMaterials(UUID projectId, LocalDate from, LocalDate to) {
    List<DailyProgressReport> dprs = dprRepository
        .findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(projectId, from, to);
    if (dprs.isEmpty()) return List.of();
    Set<UUID> dprIds = dprs.stream().map(DailyProgressReport::getId).collect(Collectors.toSet());
    return dprMaterialRepository.findByDprIdIn(dprIds);
  }

  private List<MaterialBreakdownRow> computeBreakdown(
      List<MaterialIssue> issues,
      List<MaterialConsumptionLog> logs,
      List<DprMaterial> dprMaterials) {

    Map<String, double[]> byName = new HashMap<>(); // [issued, consumed, wastage, rateSum, rateCount]
    for (MaterialConsumptionLog l : logs) {
      String name = l.getMaterialName();
      if (name == null) continue;
      double[] acc = byName.computeIfAbsent(name, k -> new double[5]);
      if (l.getConsumed() != null) acc[1] += l.getConsumed().doubleValue();
    }
    for (DprMaterial m : dprMaterials) {
      String name = m.getMaterialName();
      if (name == null) continue;
      double[] acc = byName.computeIfAbsent(name, k -> new double[5]);
      if (m.getQuantity() != null) acc[2] += 0d; // placeholder so name shows up
      if (m.getUnitRate() != null) {
        acc[3] += m.getUnitRate().doubleValue();
        acc[4] += 1d;
      }
    }
    // Issues are keyed by material_id, not name; pulling the name requires a join we skip
    // in Phase 1. The aggregate "issuedQty" still flows into the headline KPIs above.
    for (MaterialIssue i : issues) {
      // No-op: per-material breakdown by name relies on consumption-log + DPR.
    }

    List<MaterialBreakdownRow> rows = new java.util.ArrayList<>(byName.size());
    for (Map.Entry<String, double[]> e : byName.entrySet()) {
      double[] v = e.getValue();
      double issued = v[0];
      double consumed = v[1];
      double wastage = Math.max(0d, issued - consumed);
      double util = issued > 0 ? consumed / issued : 0d;
      double avgRate = v[4] > 0 ? v[3] / v[4] : 0d;
      rows.add(new MaterialBreakdownRow(
          e.getKey(),
          round3(issued),
          round3(consumed),
          round3(wastage),
          round4(util),
          round2(avgRate)));
    }
    rows.sort(Comparator.comparingDouble(MaterialBreakdownRow::consumedQty).reversed());
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
