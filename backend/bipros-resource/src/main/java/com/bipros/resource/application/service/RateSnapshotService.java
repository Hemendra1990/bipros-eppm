package com.bipros.resource.application.service;

import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the unit rate for a {@link ResourceAssignment} on a given report date. Used by the
 * DPR write path to snapshot the rate onto each child row, and by the resource-picker API to
 * preview the rate the supervisor would pay if they pick that resource today.
 *
 * <p>Lookup chain (matches {@code ResourceAssignmentCostRollupListener.resolveRate} for AC/PV
 * consistency):
 * <ol>
 *   <li>Effective {@link ResourceRate} for {@code (resourceId, rateType)} where
 *       {@code effective_date ≤ reportDate} AND ({@code effective_to} is null or
 *       {@code effective_to ≥ reportDate}). Picks the row with the latest effective date.</li>
 *   <li>{@code ProjectResource.rateOverride} for {@code (projectId, resourceId)}.</li>
 *   <li>{@code Resource.costPerUnit}.</li>
 *   <li>Null — caller surfaces a "rate-missing" warning.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class RateSnapshotService {

  private final ResourceAssignmentRepository assignmentRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceRateRepository rateRepository;
  private final ProjectResourceService projectResourceService;

  public RateSnapshot resolveByAssignment(UUID assignmentId, LocalDate reportDate) {
    ResourceAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
    if (assignment == null) {
      return new RateSnapshot(null, assignmentId, null, null, null);
    }
    return resolve(assignment, reportDate);
  }

  public RateSnapshot resolve(ResourceAssignment assignment, LocalDate reportDate) {
    UUID resourceId = assignment.getResourceId();
    if (resourceId == null) {
      return new RateSnapshot(null, assignment.getId(), null, null, null);
    }
    Resource resource = resourceRepository.findById(resourceId).orElse(null);
    String resourceName = resource == null ? null : resource.getName();
    String unitRateBasis = deriveBasis(resource);

    BigDecimal rate = resolveRate(assignment, reportDate);
    return new RateSnapshot(resourceId, assignment.getId(), rate, unitRateBasis, resourceName);
  }

  /**
   * Two-tier lookup chain. We try the rate table first (the proper source of truth, with
   * effective-dating + rate-type slicing) and fall back to the rate override / Resource.costPerUnit
   * pair to stay in sync with the EVM cost listener.
   */
  private BigDecimal resolveRate(ResourceAssignment assignment, LocalDate reportDate) {
    UUID resourceId = assignment.getResourceId();
    String rateType = assignment.getRateType();

    // 1. Rate table — effective-dated + rate-type-aware. Only consider rows whose window covers
    //    the report date and whose rate_type matches when one is specified on the assignment.
    List<ResourceRate> candidates = rateType == null || rateType.isBlank()
        ? rateRepository.findByResourceIdOrderByEffectiveDateDesc(resourceId)
        : rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(resourceId, rateType);
    LocalDate effectiveOn = reportDate != null ? reportDate : LocalDate.now();
    Optional<ResourceRate> picked = candidates.stream()
        .filter(r -> r.getEffectiveDate() != null && !r.getEffectiveDate().isAfter(effectiveOn))
        .filter(r -> r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(effectiveOn))
        .max(Comparator.comparing(ResourceRate::getEffectiveDate));
    if (picked.isPresent() && picked.get().getPricePerUnit() != null) {
      return picked.get().getPricePerUnit();
    }

    // 2. ProjectResource pool override.
    BigDecimal override = projectResourceService.resolveRateOverride(
        assignment.getProjectId(), resourceId);
    if (override != null) return override;

    // 3. Resource.costPerUnit fallback.
    Resource resource = resourceRepository.findById(resourceId).orElse(null);
    return resource == null ? null : resource.getCostPerUnit();
  }

  /**
   * Map {@code Resource.unit} to a coarse rate basis for the cost formula. Hour / Day / Each.
   * Unknown / null defaults to {@code DAY}.
   */
  static String deriveBasis(Resource resource) {
    if (resource == null || resource.getUnit() == null) return "DAY";
    String u = resource.getUnit().trim().toUpperCase();
    if (u.contains("HOUR") || u.equals("HR")) return "HOUR";
    if (u.contains("DAY") || u.contains("SHIFT")) return "DAY";
    if (u.contains("MIN")) return "HOUR";
    return "EACH";
  }

  /**
   * Snapshot of the resolved rate for one resource at one date. {@code unitRate} and
   * {@code resourceId} may be null when the assignment is role-only or the lookup fails;
   * the caller writes a warning and persists null cost in that case.
   */
  public record RateSnapshot(
      UUID resourceId,
      UUID resourceAssignmentId,
      BigDecimal unitRate,
      String unitRateBasis,
      String resourceName) {}
}
