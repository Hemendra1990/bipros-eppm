package com.bipros.resource.domain.service;

import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.model.WorkActivity;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.WorkActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the effective productivity norm for a {@code (workActivity, resource)} pair.
 *
 * <p>Fallback order:
 * <ol>
 *   <li>norm scoped to the specific resource ({@code resource_id})</li>
 *   <li>norm scoped to the resource's type ({@code resource_type_def_id})</li>
 *   <li>{@code Resource.standardOutputPerDay} (legacy denormalised default)</li>
 *   <li>empty</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class ProductivityNormLookupService {

  private final ProductivityNormRepository normRepository;
  private final WorkActivityRepository workActivityRepository;
  private final ResourceRepository resourceRepository;

  public ResolvedNorm resolve(UUID workActivityId, UUID resourceId) {
    if (workActivityId == null) {
      return ResolvedNorm.none(null, resourceId);
    }
    Resource resource = resourceId == null ? null : resourceRepository.findById(resourceId).orElse(null);

    if (resource != null) {
      Optional<ProductivityNorm> specific =
          normRepository.findFirstByWorkActivityIdAndResourceId(workActivityId, resource.getId());
      if (specific.isPresent()) {
        return materialise(specific.get(), ResolvedNorm.Source.SPECIFIC_RESOURCE, resource.getId());
      }
      ResourceTypeDef def = resource.getResourceTypeDef();
      if (def != null) {
        Optional<ProductivityNorm> typeLevel = normRepository
            .findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefId(workActivityId, def.getId());
        if (typeLevel.isPresent()) {
          return materialise(typeLevel.get(), ResolvedNorm.Source.RESOURCE_TYPE, resource.getId());
        }
      }
      if (resource.getStandardOutputPerDay() != null) {
        return new ResolvedNorm(
            BigDecimal.valueOf(resource.getStandardOutputPerDay()),
            resource.getStandardOutputUnit(),
            ResolvedNorm.Source.RESOURCE_LEGACY,
            null,
            workActivityId,
            resource.getId());
      }
    }
    return ResolvedNorm.none(workActivityId, resourceId);
  }

  /** Convenience for callers that only have an activity name (e.g. seeders ingesting BOQ rows). */
  public ResolvedNorm resolveByName(String activityName, UUID resourceId) {
    if (activityName == null || activityName.isBlank()) {
      return ResolvedNorm.none(null, resourceId);
    }
    Optional<WorkActivity> wa = workActivityRepository.findByNameIgnoreCase(activityName.trim());
    return wa.map(w -> resolve(w.getId(), resourceId))
        .orElseGet(() -> ResolvedNorm.none(null, resourceId));
  }

  private ResolvedNorm materialise(ProductivityNorm norm, ResolvedNorm.Source source, UUID resourceId) {
    return new ResolvedNorm(
        norm.getOutputPerDay(),
        norm.getUnit(),
        source,
        norm.getId(),
        norm.getWorkActivity() == null ? null : norm.getWorkActivity().getId(),
        resourceId);
  }
}
