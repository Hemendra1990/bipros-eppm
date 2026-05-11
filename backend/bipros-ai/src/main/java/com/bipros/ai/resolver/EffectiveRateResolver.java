package com.bipros.ai.resolver;

import com.bipros.resource.domain.model.ProjectResource;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ProjectResourceRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the effective rate + unit + basis for a (project, resource) pair.
 *
 * <p>The two-tier chain mirrors {@code ResourceAssignmentService.computePlannedCost}
 * (lines 130–148): {@code ProjectResource.rateOverride} takes precedence over
 * {@code Resource.costPerUnit}. {@code ProjectResource.customUnit} similarly overrides
 * {@code Resource.unit} for unit display.
 *
 * <p>Shared by every cost-returning AI tool so the LLM can disclose pool overrides.
 */
@Service
@RequiredArgsConstructor
public class EffectiveRateResolver {

  private final ProjectResourceRepository projectResourceRepository;
  private final ResourceRepository resourceRepository;

  public EffectiveRate resolve(UUID projectId, UUID resourceId) {
    if (resourceId == null) {
      return new EffectiveRate(null, EffectiveRate.Source.NONE, null, null, false, null, null);
    }

    Resource resource = resourceRepository.findById(resourceId).orElse(null);
    String baseUnit = resource == null ? null : resource.getUnit();
    BigDecimal baseRate = resource == null ? null : resource.getCostPerUnit();
    UUID rateMasterId = resource == null ? null : resource.getRateMasterId();

    Optional<ProjectResource> pool =
        projectId == null
            ? Optional.empty()
            : projectResourceRepository.findByProjectIdAndResourceId(projectId, resourceId);

    if (pool.isPresent() && pool.get().getRateOverride() != null) {
      ProjectResource pr = pool.get();
      String unit = (pr.getCustomUnit() != null && !pr.getCustomUnit().isBlank())
          ? pr.getCustomUnit()
          : baseUnit;
      return new EffectiveRate(
          pr.getRateOverride(),
          EffectiveRate.Source.OVERRIDE,
          unit,
          deriveBasis(unit),
          true,
          pr.getId(),
          rateMasterId);
    }
    if (baseRate != null) {
      return new EffectiveRate(
          baseRate,
          EffectiveRate.Source.RESOURCE,
          baseUnit,
          deriveBasis(baseUnit),
          false,
          pool.map(ProjectResource::getId).orElse(null),
          rateMasterId);
    }
    return new EffectiveRate(
        null,
        EffectiveRate.Source.NONE,
        baseUnit,
        deriveBasis(baseUnit),
        false,
        pool.map(ProjectResource::getId).orElse(null),
        rateMasterId);
  }

  static String deriveBasis(String unit) {
    if (unit == null) return null;
    String u = unit.trim().toUpperCase();
    if (u.isEmpty()) return null;
    if (u.contains("HOUR") || u.equals("HR") || u.contains("MIN")) return "HOUR";
    if (u.contains("DAY") || u.contains("SHIFT")) return "DAY";
    return "EACH";
  }
}
