package com.bipros.resource.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
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

  public ResourceRateResponse createRate(UUID resourceId, CreateResourceRateRequest request) {
    log.info("Creating rate for resource: resourceId={}, rateType={}", resourceId, request.rateType());

    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }

    ResourceRate rate = ResourceRate.builder()
        .resourceId(resourceId)
        .rateType(request.rateType())
        .pricePerUnit(request.pricePerUnit())
        .effectiveDate(request.effectiveDate())
        .maxUnitsPerTime(request.maxUnitsPerTime())
        .build();

    ResourceRate saved = rateRepository.save(rate);
    log.info("Rate created: id={}", saved.getId());
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

    rate.setRateType(request.rateType());
    rate.setPricePerUnit(request.pricePerUnit());
    rate.setEffectiveDate(request.effectiveDate());
    rate.setMaxUnitsPerTime(request.maxUnitsPerTime());

    ResourceRate updated = rateRepository.save(rate);
    log.info("Rate updated: id={}", id);
    return ResourceRateResponse.from(updated);
  }

  public void deleteRate(UUID id) {
    log.info("Deleting rate: id={}", id);
    if (!rateRepository.existsById(id)) {
      throw new ResourceNotFoundException("ResourceRate", id);
    }
    rateRepository.deleteById(id);
    log.info("Rate deleted: id={}", id);
  }
}
