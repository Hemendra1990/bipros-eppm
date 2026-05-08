package com.bipros.reporting.application.service;

import com.bipros.reporting.application.dto.CapacityUtilizationReport.Budgeted;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Productivity-norm fallback chain shared by the capacity-utilization report and the
 * supervisor-performance report. Same semantics as {@code ProductivityNormLookupService} but
 * exposed as native SQL through the shared {@link EntityManager} so this module stays free of
 * Maven deps on {@code bipros-resource}.
 *
 * <p>For Manpower norms we prefer {@code output_per_man_per_day} so the rate is per-person-per-day
 * (matches DPR rows where each manpower line carries {@code nos × hours}). Equipment norms only
 * populate {@code output_per_day}; COALESCE falls through to that, which is the equipment's
 * per-machine-per-day rate.
 */
@Component
@RequiredArgsConstructor
public class ProductivityNormResolver {

  @PersistenceContext private EntityManager em;

  /**
   * Specific-resource → type-level → legacy {@code resource_equipment_details.standard_output_per_day}.
   * Used by the capacity-utilization report where each row already carries a representative
   * resource id.
   */
  public Budgeted resolveByResource(UUID workActivityId, UUID resourceId) {
    if (workActivityId == null || resourceId == null) {
      return new Budgeted(null, "NONE");
    }
    BigDecimal specific = singleBigDecimal(
        "SELECT COALESCE(n.output_per_man_per_day, n.output_per_day) "
            + "FROM resource.productivity_norms n "
            + "WHERE n.work_activity_id = :wa AND n.resource_id = :res",
        Map.of("wa", workActivityId, "res", resourceId));
    if (specific != null) {
      return new Budgeted(specific, "SPECIFIC_RESOURCE");
    }
    BigDecimal typeLevel = singleBigDecimal(
        "SELECT COALESCE(n.output_per_man_per_day, n.output_per_day) "
            + "FROM resource.productivity_norms n "
            + "JOIN resource.resources r ON r.resource_type_id = n.resource_type_id "
            + "WHERE n.work_activity_id = :wa AND n.resource_id IS NULL "
            + "  AND r.id = :res",
        Map.of("wa", workActivityId, "res", resourceId));
    if (typeLevel != null) {
      return new Budgeted(typeLevel, "RESOURCE_TYPE");
    }
    // 3) Work-activity-only norm (no resource_type_id) — applies to any resource on that
    //    activity. Common in seeded data where the norm describes the activity output rather
    //    than a specific trade. Picks the highest-priority value: output_per_man_per_day first
    //    (manpower-friendly), output_per_day next, ordered by created_at for determinism.
    BigDecimal workActivityOnly = singleBigDecimal(
        "SELECT COALESCE(n.output_per_man_per_day, n.output_per_day) "
            + "FROM resource.productivity_norms n "
            + "WHERE n.work_activity_id = :wa AND n.resource_id IS NULL "
            + "  AND n.resource_type_id IS NULL "
            + "ORDER BY (n.output_per_man_per_day IS NULL), n.created_at NULLS LAST",
        Map.of("wa", workActivityId));
    if (workActivityOnly != null) {
      return new Budgeted(workActivityOnly, "WORK_ACTIVITY");
    }
    BigDecimal legacy = singleBigDecimal(
        "SELECT d.standard_output_per_day FROM resource.resource_equipment_details d "
            + "WHERE d.resource_id = :res",
        Map.of("res", resourceId));
    if (legacy != null) {
      return new Budgeted(legacy, "RESOURCE_LEGACY");
    }
    return new Budgeted(null, "NONE");
  }

  /**
   * Type-driven lookup (no specific-resource path). Used by the supervisor-performance trade /
   * equipment rollup where the canonical key is a trade or equipment-type, not a single resource.
   *
   * <p>Filters by {@code norm_type} so a MANPOWER norm is never applied to equipment and vice
   * versa. For MANPOWER, prefers {@code output_per_man_per_day} (per-person-per-day rate matches
   * how DPR rows count person-hours). For EQUIPMENT, prefers {@code output_per_day} (per-machine-
   * per-day rate matches how DPR rows count machine-hours).
   *
   * <p>Falls back to a work-activity-only norm of the SAME {@code normType} when no type-specific
   * match exists. Returns NONE when the work activity has no norm of the requested kind — caller
   * should display "—%" rather than fabricating a number.
   */
  public Budgeted resolveByResourceType(UUID workActivityId, UUID resourceTypeId, String normType) {
    if (workActivityId == null || normType == null) {
      return new Budgeted(null, "NONE");
    }
    String preferredColumn = "EQUIPMENT".equalsIgnoreCase(normType)
        ? "n.output_per_day"
        : "COALESCE(n.output_per_man_per_day, n.output_per_day)";

    if (resourceTypeId != null) {
      BigDecimal typeLevel = singleBigDecimal(
          "SELECT " + preferredColumn + " "
              + "FROM resource.productivity_norms n "
              + "WHERE n.work_activity_id = :wa "
              + "  AND n.norm_type = :nt "
              + "  AND n.resource_id IS NULL "
              + "  AND n.resource_type_id = :rt",
          Map.of("wa", workActivityId, "nt", normType, "rt", resourceTypeId));
      if (typeLevel != null) {
        return new Budgeted(typeLevel, "RESOURCE_TYPE");
      }
    }
    BigDecimal workActivityOnly = singleBigDecimal(
        "SELECT " + preferredColumn + " "
            + "FROM resource.productivity_norms n "
            + "WHERE n.work_activity_id = :wa "
            + "  AND n.norm_type = :nt "
            + "  AND n.resource_id IS NULL "
            + "  AND n.resource_type_id IS NULL "
            + "ORDER BY (" + preferredColumn + " IS NULL), n.created_at NULLS LAST",
        Map.of("wa", workActivityId, "nt", normType));
    if (workActivityOnly != null) {
      return new Budgeted(workActivityOnly, "WORK_ACTIVITY");
    }
    return new Budgeted(null, "NONE");
  }

  /** Backwards-compat overload — defers to {@code MANPOWER} which was the prior implicit default. */
  public Budgeted resolveByResourceType(UUID workActivityId, UUID resourceTypeId) {
    return resolveByResourceType(workActivityId, resourceTypeId, "MANPOWER");
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
}
