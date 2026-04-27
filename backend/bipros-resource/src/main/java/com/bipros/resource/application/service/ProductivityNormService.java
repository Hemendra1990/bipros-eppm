package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateProductivityNormRequest;
import com.bipros.resource.application.dto.ProductivityNormResponse;
import com.bipros.resource.domain.model.ProductivityNorm;
import com.bipros.resource.domain.model.ProductivityNormType;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.model.WorkActivity;
import com.bipros.resource.domain.repository.ProductivityNormRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceTypeDefRepository;
import com.bipros.resource.domain.repository.WorkActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ProductivityNormService {

  private final ProductivityNormRepository repository;
  private final WorkActivityRepository workActivityRepository;
  private final ResourceTypeDefRepository resourceTypeDefRepository;
  private final ResourceRepository resourceRepository;
  private final AuditService auditService;

  public ProductivityNormResponse create(CreateProductivityNormRequest request) {
    log.info("Creating productivity norm: type={}, workActivityId={}, resourceTypeDefId={}, resourceId={}",
        request.normType(), request.workActivityId(), request.resourceTypeDefId(), request.resourceId());
    ProductivityNorm norm = toEntity(new ProductivityNorm(), request);
    ProductivityNorm saved = repository.save(norm);
    auditService.logCreate("ProductivityNorm", saved.getId(), ProductivityNormResponse.from(saved));
    return ProductivityNormResponse.from(saved);
  }

  public List<ProductivityNormResponse> createBulk(List<CreateProductivityNormRequest> requests) {
    log.info("Bulk-creating {} productivity norms", requests.size());
    List<ProductivityNorm> entities = requests.stream()
        .map(r -> toEntity(new ProductivityNorm(), r))
        .toList();
    return repository.saveAll(entities).stream()
        .map(ProductivityNormResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ProductivityNormResponse get(UUID id) {
    return ProductivityNormResponse.from(find(id));
  }

  @Transactional(readOnly = true)
  public List<ProductivityNormResponse> list(ProductivityNormType normType) {
    List<ProductivityNorm> entities = normType == null
        ? repository.findAll()
        : repository.findByNormType(normType);
    return entities.stream().map(ProductivityNormResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<ProductivityNormResponse> listByWorkActivity(UUID workActivityId) {
    return repository.findByWorkActivityId(workActivityId).stream()
        .map(ProductivityNormResponse::from)
        .toList();
  }

  public ProductivityNormResponse update(UUID id, CreateProductivityNormRequest request) {
    ProductivityNorm norm = find(id);
    toEntity(norm, request);
    ProductivityNorm updated = repository.save(norm);
    auditService.logUpdate("ProductivityNorm", id, "norm", null, ProductivityNormResponse.from(updated));
    return ProductivityNormResponse.from(updated);
  }

  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("ProductivityNorm", id);
    }
    repository.deleteById(id);
    auditService.logDelete("ProductivityNorm", id);
  }

  private ProductivityNorm find(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ProductivityNorm", id));
  }

  private ProductivityNorm toEntity(ProductivityNorm norm, CreateProductivityNormRequest r) {
    if (r.resourceTypeDefId() != null && r.resourceId() != null) {
      throw new BusinessRuleException("NORM_SCOPE_AMBIGUOUS",
          "Provide either resourceTypeDefId (default scope) or resourceId (override) — not both");
    }

    WorkActivity workActivity = resolveWorkActivity(r);
    enforceScopeUniqueness(norm, workActivity.getId(), r.resourceTypeDefId(), r.resourceId());
    norm.setWorkActivity(workActivity);
    norm.setActivityName(workActivity.getName()); // keep legacy column synced

    norm.setResourceTypeDef(r.resourceTypeDefId() == null ? null
        : resourceTypeDefRepository.findById(r.resourceTypeDefId())
            .orElseThrow(() -> new ResourceNotFoundException("ResourceTypeDef", r.resourceTypeDefId())));

    Resource resource = r.resourceId() == null ? null
        : resourceRepository.findById(r.resourceId())
            .orElseThrow(() -> new ResourceNotFoundException("Resource", r.resourceId()));
    norm.setResource(resource);

    norm.setNormType(r.normType());
    norm.setUnit(r.unit());
    norm.setOutputPerManPerDay(r.outputPerManPerDay());
    norm.setOutputPerHour(r.outputPerHour());
    norm.setCrewSize(r.crewSize());
    norm.setWorkingHoursPerDay(r.workingHoursPerDay());
    norm.setFuelLitresPerHour(r.fuelLitresPerHour());
    norm.setEquipmentSpec(r.equipmentSpec());
    norm.setRemarks(r.remarks());

    BigDecimal derived = r.outputPerDay();
    if (derived == null) {
      if (r.normType() == ProductivityNormType.MANPOWER
          && r.outputPerManPerDay() != null && r.crewSize() != null) {
        derived = r.outputPerManPerDay().multiply(BigDecimal.valueOf(r.crewSize()));
      } else if (r.normType() == ProductivityNormType.EQUIPMENT
          && r.outputPerHour() != null && r.workingHoursPerDay() != null) {
        derived = r.outputPerHour().multiply(BigDecimal.valueOf(r.workingHoursPerDay()));
      }
    }
    norm.setOutputPerDay(derived);
    return norm;
  }

  /**
   * Mirrors the Postgres partial unique indexes ({@code uk_norm_by_resource}, {@code uk_norm_by_type})
   * at the service layer so duplicate-scope creates are rejected even in dev (where
   * {@code ddl-auto=create-drop} skips Liquibase and the indexes don't exist).
   */
  private void enforceScopeUniqueness(
      ProductivityNorm current, UUID workActivityId, UUID resourceTypeDefId, UUID resourceId) {
    if (resourceId != null) {
      repository.findFirstByWorkActivityIdAndResourceId(workActivityId, resourceId)
          .filter(existing -> !existing.getId().equals(current.getId()))
          .ifPresent(existing -> {
            throw new BusinessRuleException("NORM_SCOPE_DUPLICATE_RESOURCE",
                "A productivity norm already exists for this activity + specific resource");
          });
    } else if (resourceTypeDefId != null) {
      repository.findFirstByWorkActivityIdAndResourceIsNullAndResourceTypeDefId(
              workActivityId, resourceTypeDefId)
          .filter(existing -> !existing.getId().equals(current.getId()))
          .ifPresent(existing -> {
            throw new BusinessRuleException("NORM_SCOPE_DUPLICATE_TYPE",
                "A productivity norm already exists for this activity + resource type");
          });
    }
  }

  private WorkActivity resolveWorkActivity(CreateProductivityNormRequest r) {
    if (r.workActivityId() != null) {
      return workActivityRepository.findById(r.workActivityId())
          .orElseThrow(() -> new ResourceNotFoundException("WorkActivity", r.workActivityId()));
    }
    if (r.activityName() != null && !r.activityName().isBlank()) {
      return workActivityRepository.findByNameIgnoreCase(r.activityName().trim())
          .orElseThrow(() -> new BusinessRuleException("UNKNOWN_WORK_ACTIVITY",
              "No WorkActivity matches activityName '" + r.activityName()
                  + "'. Create the WorkActivity first or supply workActivityId."));
    }
    throw new BusinessRuleException("WORK_ACTIVITY_REQUIRED",
        "workActivityId is required (or supply activityName matching an existing WorkActivity)");
  }
}
