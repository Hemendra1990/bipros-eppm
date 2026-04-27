package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.CapacityUtilizationReport;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.Budgeted;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.GroupKey;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.Period;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.Row;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.WorkActivityRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Capacity Utilization report — joins {@code project.daily_activity_resource_outputs} (Phase 2
 * actuals) with {@code activity.activities.work_activity_id} (Phase 1 link) and
 * {@code resource.resources.resource_type_def_id}, then resolves the budgeted norm via
 * {@code resource.productivity_norms} with the same fallback chain as
 * {@code ProductivityNormLookupService}: specific-resource norm → type-level norm →
 * {@code resource.standardOutputPerDay}.
 *
 * <p>Implemented as native SQL through the shared {@link EntityManager} so this module does
 * <em>not</em> need a Maven dependency on {@code bipros-activity} or {@code bipros-resource}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CapacityUtilizationReportService {

  private static final double DEFAULT_HOURS_PER_DAY = 8.0;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal UTILIZATION_CAP = BigDecimal.valueOf(999);

  @PersistenceContext private EntityManager em;

  @Transactional(readOnly = true)
  public CapacityUtilizationReport build(
      UUID projectId, LocalDate fromDate, LocalDate toDate, String groupBy, String normType) {

    LocalDate effectiveTo = toDate == null ? LocalDate.now() : toDate;
    LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfYear(1) : fromDate;
    String resolvedGroupBy = groupBy == null ? "RESOURCE_TYPE" : groupBy.toUpperCase();
    boolean groupByResource = "RESOURCE".equals(resolvedGroupBy);

    // 1. Pull all output rows in the cumulative window — one query, grouped client-side.
    @SuppressWarnings("unchecked")
    List<Object[]> raw = em.createNativeQuery(
            "SELECT "
                + "  o.output_date, "
                + "  a.work_activity_id, "
                + "  wa.code, "
                + "  wa.name, "
                + "  wa.default_unit, "
                + "  r.resource_type_def_id, "
                + "  rtd.name AS resource_type_def_name, "
                + "  r.id AS resource_id, "
                + "  r.code AS resource_code, "
                + "  r.name AS resource_name, "
                + "  o.qty_executed, "
                + "  o.hours_worked, "
                + "  o.days_worked "
                + "FROM project.daily_activity_resource_outputs o "
                + "JOIN activity.activities a ON a.id = o.activity_id "
                + "JOIN resource.work_activities wa ON wa.id = a.work_activity_id "
                + "JOIN resource.resources r ON r.id = o.resource_id "
                + "LEFT JOIN resource.resource_type_defs rtd ON rtd.id = r.resource_type_def_id "
                + "WHERE o.project_id = :projectId "
                + "  AND o.output_date BETWEEN :fromDate AND :toDate "
                + "  AND a.work_activity_id IS NOT NULL")
        .setParameter("projectId", projectId)
        .setParameter("fromDate", effectiveFrom)
        .setParameter("toDate", effectiveTo)
        .getResultList();

    // 2. Aggregate by (workActivity, group-key). Group key = resourceTypeDefId or resourceId.
    Map<String, Aggregate> byBucket = new LinkedHashMap<>();
    for (Object[] r : raw) {
      LocalDate outputDate = ((java.sql.Date) r[0]).toLocalDate();
      UUID workActivityId = (UUID) r[1];
      String workActivityCode = (String) r[2];
      String workActivityName = (String) r[3];
      String workActivityDefaultUnit = (String) r[4];
      UUID resourceTypeDefId = (UUID) r[5];
      String resourceTypeDefName = (String) r[6];
      UUID resourceId = (UUID) r[7];
      String resourceCode = (String) r[8];
      String resourceName = (String) r[9];
      BigDecimal qty = (BigDecimal) r[10];
      Number hoursWorked = (Number) r[11];
      Number daysWorked = (Number) r[12];

      double effectiveDays;
      if (daysWorked != null) {
        effectiveDays = daysWorked.doubleValue();
      } else if (hoursWorked != null) {
        effectiveDays = hoursWorked.doubleValue() / DEFAULT_HOURS_PER_DAY;
      } else {
        effectiveDays = 0.0;
      }

      UUID groupResourceTypeId = groupByResource ? null : resourceTypeDefId;
      UUID groupResourceId = groupByResource ? resourceId : null;
      String displayLabel = groupByResource
          ? (resourceCode != null ? resourceCode + " — " + resourceName : resourceName)
          : (resourceTypeDefName != null ? resourceTypeDefName : "(no type)");
      String bucketKey = workActivityId + "|" + (groupByResource ? resourceId : nullSafe(resourceTypeDefId));

      Aggregate agg = byBucket.computeIfAbsent(bucketKey, k -> new Aggregate(
          new GroupKey(groupResourceTypeId, groupResourceId, displayLabel),
          new WorkActivityRef(workActivityId, workActivityCode, workActivityName, workActivityDefaultUnit),
          // Pick a representative resourceId for budgeted-norm lookup. When grouping by type, any
          // resource in that type works (norm fallback chain considers type then specific).
          resourceId));

      agg.addCumulative(qty, effectiveDays);
      if (isInMonth(outputDate, effectiveTo)) {
        agg.addMonth(qty, effectiveDays);
      }
      if (outputDate.equals(effectiveTo)) {
        agg.addDay(qty, effectiveDays);
      }
    }

    // 3. Resolve budgeted norm per bucket and assemble rows.
    List<Row> rows = new ArrayList<>(byBucket.size());
    for (Aggregate agg : byBucket.values()) {
      Budgeted budgeted = resolveBudgeted(agg.workActivity.id(), agg.representativeResourceId);
      Period day = period(agg.dayQty, agg.dayDays, budgeted);
      Period month = period(agg.monthQty, agg.monthDays, budgeted);
      Period cum = period(agg.cumQty, agg.cumDays, budgeted);
      rows.add(new Row(agg.groupKey, agg.workActivity, budgeted, day, month, cum));
    }

    // Optional normType filter — discard rows whose norm record's normType doesn't match. We
    // post-filter rather than pushing into SQL because the budgeted norm is resolved row-by-row.
    if (normType != null && !normType.isBlank()) {
      String normTypeUpper = normType.toUpperCase();
      rows.removeIf(row -> !matchesNormType(row, normTypeUpper));
    }

    return new CapacityUtilizationReport(projectId, effectiveFrom, effectiveTo, resolvedGroupBy, normType, rows);
  }

  // ─── Norm resolution (native SQL — same fallback as ProductivityNormLookupService) ──────────
  private Budgeted resolveBudgeted(UUID workActivityId, UUID resourceId) {
    if (workActivityId == null || resourceId == null) {
      return new Budgeted(null, "NONE");
    }
    // 1) specific-resource norm
    BigDecimal specific = singleBigDecimal(
        "SELECT n.output_per_day FROM resource.productivity_norms n "
            + "WHERE n.work_activity_id = :wa AND n.resource_id = :res",
        Map.of("wa", workActivityId, "res", resourceId));
    if (specific != null) {
      return new Budgeted(specific, "SPECIFIC_RESOURCE");
    }
    // 2) type-level norm via the resource's type_def
    BigDecimal typeLevel = singleBigDecimal(
        "SELECT n.output_per_day FROM resource.productivity_norms n "
            + "JOIN resource.resources r ON r.resource_type_def_id = n.resource_type_def_id "
            + "WHERE n.work_activity_id = :wa AND n.resource_id IS NULL "
            + "  AND r.id = :res",
        Map.of("wa", workActivityId, "res", resourceId));
    if (typeLevel != null) {
      return new Budgeted(typeLevel, "RESOURCE_TYPE");
    }
    // 3) legacy Resource.standardOutputPerDay
    BigDecimal legacy = singleBigDecimal(
        "SELECT r.standard_output_per_day FROM resource.resources r WHERE r.id = :res",
        Map.of("res", resourceId));
    if (legacy != null) {
      return new Budgeted(legacy, "RESOURCE_LEGACY");
    }
    return new Budgeted(null, "NONE");
  }

  private boolean matchesNormType(Row row, String normTypeUpper) {
    if (row.budgeted() == null || row.workActivity() == null) {
      return false;
    }
    Object stored = em.createNativeQuery(
            "SELECT norm_type FROM resource.productivity_norms WHERE work_activity_id = :wa")
        .setParameter("wa", row.workActivity().id())
        .setMaxResults(1)
        .getResultList().stream().findFirst().orElse(null);
    return stored != null && normTypeUpper.equals(stored.toString());
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────────────────────
  private Period period(BigDecimal qty, double days, Budgeted budgeted) {
    BigDecimal actualDaysExact = days > 0 ? BigDecimal.valueOf(days) : BigDecimal.ZERO;
    BigDecimal budgetedDaysExact = (budgeted.outputPerDay() != null && budgeted.outputPerDay().signum() > 0)
        ? qty.divide(budgeted.outputPerDay(), 8, RoundingMode.HALF_UP)
        : null;
    BigDecimal actualOutputPerDayExact = (days > 0)
        ? qty.divide(actualDaysExact, 8, RoundingMode.HALF_UP)
        : null;
    // Productivity utilization: how much of the planned output was achieved.
    //   util = actualOutputPerDay / budgetedOutputPerDay   (equivalently: budgetedDays / actualDays)
    // <100 means under-performance; >=100 means meeting or exceeding the planned norm.
    // Compute from un-rounded values then round the final result, otherwise the percentage
    // drifts (e.g. 0.875 → 0.88 when rounded → util shows 88% instead of 87.5%).
    BigDecimal utilization = (budgetedDaysExact != null && actualDaysExact.signum() > 0)
        ? budgetedDaysExact.divide(actualDaysExact, 8, RoundingMode.HALF_UP).multiply(HUNDRED)
        : null;
    if (utilization != null) {
      utilization = utilization.min(UTILIZATION_CAP).setScale(2, RoundingMode.HALF_UP);
    }
    return new Period(
        round(qty),
        budgetedDaysExact != null ? round(budgetedDaysExact) : null,
        round(actualDaysExact),
        actualOutputPerDayExact != null ? round(actualOutputPerDayExact) : null,
        utilization);
  }

  private static BigDecimal round(BigDecimal v) {
    return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
  }

  private static boolean isInMonth(LocalDate date, LocalDate anchor) {
    return YearMonth.from(date).equals(YearMonth.from(anchor));
  }

  private static String nullSafe(UUID id) {
    return id == null ? "—" : id.toString();
  }

  @SuppressWarnings("unchecked")
  private BigDecimal singleBigDecimal(String sql, Map<String, Object> params) {
    var query = em.createNativeQuery(sql);
    params.forEach(query::setParameter);
    List<Object> rows = query.setMaxResults(1).getResultList();
    if (rows.isEmpty() || rows.get(0) == null) return null;
    Object o = rows.get(0);
    if (o instanceof BigDecimal bd) return bd;
    if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
    return null;
  }

  /** Mutable accumulator scoped to a single (workActivity × group-key) bucket. */
  private static final class Aggregate {
    final GroupKey groupKey;
    final WorkActivityRef workActivity;
    final UUID representativeResourceId;
    BigDecimal dayQty = BigDecimal.ZERO;
    double dayDays = 0;
    BigDecimal monthQty = BigDecimal.ZERO;
    double monthDays = 0;
    BigDecimal cumQty = BigDecimal.ZERO;
    double cumDays = 0;

    Aggregate(GroupKey groupKey, WorkActivityRef workActivity, UUID representativeResourceId) {
      this.groupKey = groupKey;
      this.workActivity = workActivity;
      this.representativeResourceId = representativeResourceId;
    }

    void addDay(BigDecimal qty, double days) { dayQty = dayQty.add(qty); dayDays += days; }
    void addMonth(BigDecimal qty, double days) { monthQty = monthQty.add(qty); monthDays += days; }
    void addCumulative(BigDecimal qty, double days) { cumQty = cumQty.add(qty); cumDays += days; }
  }
}
