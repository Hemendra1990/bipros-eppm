package com.bipros.resource.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateResourceRateRequest;
import com.bipros.resource.application.dto.ResourceRateResponse;
import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ResourceRateService {

  private final ResourceRateRepository rateRepository;
  private final ResourceRepository resourceRepository;
  private final AuditService auditService;

  public ResourceRateResponse createRate(UUID resourceId, CreateResourceRateRequest request) {
    String effectiveType = request.effectiveRateType();
    java.math.BigDecimal effectivePrice = request.effectivePricePerUnit();
    log.info("Creating rate for resource: resourceId={}, rateType={}", resourceId, effectiveType);

    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }
    if (effectivePrice == null) {
      throw new com.bipros.common.exception.BusinessRuleException(
          "INVALID_RATE", "A price value is required (pricePerUnit, standardRate, or overtimeRate)");
    }

    enforceNoOverlap(resourceId, effectiveType, request.category(),
        request.effectiveDate(), request.effectiveTo(), null);

    ResourceRate rate = ResourceRate.builder()
        .resourceId(resourceId)
        .rateType(effectiveType)
        .pricePerUnit(effectivePrice)
        .effectiveDate(request.effectiveDate())
        .effectiveTo(request.effectiveTo())
        .maxUnitsPerTime(request.maxUnitsPerTime())
        .budgetedRate(request.budgetedRate() != null ? request.budgetedRate() : effectivePrice)
        .actualRate(request.actualRate())
        .category(request.category())
        .approvedByUserId(request.approvedByUserId())
        .approvedByName(request.approvedByName())
        .build();

    ResourceRate saved = rateRepository.save(rate);
    log.info("Rate created: id={}", saved.getId());

    auditService.logCreate("ResourceRate", saved.getId(), ResourceRateResponse.from(saved));

    // When the client supplied an overtimeRate alongside a standard rate, also materialise a
    // paired OVERTIME row so subsequent cost calculations see both legs.
    if (request.overtimeRate() != null && "STANDARD".equalsIgnoreCase(effectiveType)) {
      ResourceRate overtime = ResourceRate.builder()
          .resourceId(resourceId)
          .rateType("OVERTIME")
          .pricePerUnit(request.overtimeRate())
          .effectiveDate(request.effectiveDate())
          .maxUnitsPerTime(request.maxUnitsPerTime())
          .build();
      rateRepository.save(overtime);
    }

    return ResourceRateResponse.from(saved);
  }

  public ResourceRateResponse getRate(UUID id) {
    log.info("Fetching rate: id={}", id);
    ResourceRate rate = rateRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceRate", id));
    return ResourceRateResponse.from(rate);
  }

  public List<ResourceRateResponse> listRatesByResource(UUID resourceId) {
    log.info("Listing rates for resource: {}", resourceId);
    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }
    return rateRepository.findByResourceId(resourceId).stream()
        .map(ResourceRateResponse::from)
        .toList();
  }

  public List<ResourceRateResponse> listRatesByResourceAndType(UUID resourceId, String rateType) {
    log.info("Listing rates for resource: {}, type: {}", resourceId, rateType);
    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }
    return rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(resourceId, rateType)
        .stream()
        .map(ResourceRateResponse::from)
        .toList();
  }

  public ResourceRateResponse updateRate(UUID id, CreateResourceRateRequest request) {
    log.info("Updating rate: id={}", id);
    ResourceRate rate = rateRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceRate", id));

    if (request.rateType() != null) rate.setRateType(request.rateType());
    if (request.effectivePricePerUnit() != null) rate.setPricePerUnit(request.effectivePricePerUnit());
    if (request.effectiveDate() != null) rate.setEffectiveDate(request.effectiveDate());
    if (request.effectiveTo() != null) rate.setEffectiveTo(request.effectiveTo());
    if (request.maxUnitsPerTime() != null) rate.setMaxUnitsPerTime(request.maxUnitsPerTime());
    if (request.budgetedRate() != null) rate.setBudgetedRate(request.budgetedRate());
    if (request.actualRate() != null) rate.setActualRate(request.actualRate());
    if (request.category() != null) rate.setCategory(request.category());
    if (request.approvedByUserId() != null) rate.setApprovedByUserId(request.approvedByUserId());
    if (request.approvedByName() != null) rate.setApprovedByName(request.approvedByName());

    enforceNoOverlap(rate.getResourceId(), rate.getRateType(), rate.getCategory(),
        rate.getEffectiveDate(), rate.getEffectiveTo(), id);

    ResourceRate updated = rateRepository.save(rate);
    log.info("Rate updated: id={}", id);

    // Audit log update
    auditService.logUpdate("ResourceRate", id, "rate", rate, ResourceRateResponse.from(updated));

    return ResourceRateResponse.from(updated);
  }

  /**
   * Reject overlapping effective-date ranges for the same (resourceId, rateType, category) —
   * the Unit Rate Master treats each rate period as exclusive so chronological ordering stays
   * unambiguous. An open-ended {@code effectiveTo} (null) overlaps anything on or after
   * {@code effectiveFrom}. Excludes {@code selfId} so edits don't collide with themselves.
   */
  private void enforceNoOverlap(UUID resourceId, String rateType,
                                com.bipros.resource.domain.model.UnitRateCategory category,
                                java.time.LocalDate from, java.time.LocalDate to,
                                UUID selfId) {
    if (from == null || rateType == null) return;
    for (ResourceRate existing : rateRepository
        .findByResourceIdAndRateTypeOrderByEffectiveDateDesc(resourceId, rateType)) {
      if (selfId != null && selfId.equals(existing.getId())) continue;
      if (category != null && existing.getCategory() != null
          && category != existing.getCategory()) continue;
      java.time.LocalDate existingFrom = existing.getEffectiveDate();
      java.time.LocalDate existingTo = existing.getEffectiveTo();
      boolean overlaps =
          (existingTo == null || !existingTo.isBefore(from))
          && (to == null || !existingFrom.isAfter(to));
      if (overlaps) {
        throw new com.bipros.common.exception.BusinessRuleException(
            "OVERLAPPING_RATE",
            "Another rate for this resource/type already covers the requested effective window");
      }
    }
  }

  public void deleteRate(UUID id) {
    log.info("Deleting rate: id={}", id);
    if (!rateRepository.existsById(id)) {
      throw new ResourceNotFoundException("ResourceRate", id);
    }
    rateRepository.deleteById(id);
    log.info("Rate deleted: id={}", id);

    // Audit log deletion
    auditService.logDelete("ResourceRate", id);
  }
}
