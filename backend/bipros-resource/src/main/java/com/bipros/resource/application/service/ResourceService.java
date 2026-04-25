package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateResourceRequest;
import com.bipros.resource.application.dto.ResourceResponse;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceTypeDefRepository;
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
public class ResourceService {

  private final ResourceRepository resourceRepository;
  private final ResourceTypeDefRepository resourceTypeDefRepository;
  private final AuditService auditService;

  public ResourceResponse createResource(CreateResourceRequest request) {
    log.info("Creating resource: code={}, defId={}, type={}", request.code(), request.resourceTypeDefId(), request.resourceType());

    ResourceTypeDef def = resolveTypeDef(request.resourceTypeDefId(), request.resourceType());
    ResourceType baseCategory = def.getBaseCategory();

    String code = request.code() != null && !request.code().isBlank()
        ? request.code() : generateResourceCode(def);
    if (resourceRepository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_RESOURCE_CODE", "Resource with code " + code + " already exists");
    }

    Resource resource = Resource.builder()
        .code(code)
        .name(request.name())
        .resourceType(baseCategory)
        .resourceTypeDef(def)
        .parentId(request.parentId())
        .calendarId(request.calendarId())
        .email(request.email())
        .phone(request.phone())
        .title(request.title())
        .maxUnitsPerDay(request.maxUnitsPerDay() != null ? request.maxUnitsPerDay() : 8.0)
        .status(ResourceStatus.ACTIVE)
        .hourlyRate(request.hourlyRate() != null ? request.hourlyRate() : 0.0)
        .costPerUse(request.costPerUse() != null ? request.costPerUse() : 0.0)
        .overtimeRate(request.overtimeRate() != null ? request.overtimeRate() : 0.0)
        .unit(request.unit())
        .capacitySpec(request.capacitySpec())
        .makeModel(request.makeModel())
        .quantityAvailable(request.quantityAvailable())
        .ownershipType(request.ownershipType())
        .standardOutputPerDay(request.standardOutputPerDay())
        .standardOutputUnit(request.standardOutputUnit())
        .fuelLitresPerHour(request.fuelLitresPerHour())
        .sortOrder(0)
        .build();

    Resource saved = resourceRepository.save(resource);
    log.info("Resource created: id={}", saved.getId());

    // Audit log creation
    auditService.logCreate("Resource", saved.getId(), ResourceResponse.from(saved));

    return ResourceResponse.from(saved);
  }

  public ResourceResponse getResource(UUID id) {
    log.info("Fetching resource: id={}", id);
    Resource resource = resourceRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Resource", id));
    return ResourceResponse.from(resource);
  }

  public List<ResourceResponse> listResources() {
    log.info("Listing all resources");
    return resourceRepository.findAll().stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public List<ResourceResponse> listResourcesByType(ResourceType type) {
    log.info("Listing resources by type: {}", type);
    return resourceRepository.findByResourceType(type).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public List<ResourceResponse> listResourceHierarchyRoots() {
    log.info("Listing resource hierarchy roots");
    return resourceRepository.findByParentIdIsNull().stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public List<ResourceResponse> listResourcesByParent(UUID parentId) {
    log.info("Listing resources by parent: {}", parentId);
    return resourceRepository.findByParentId(parentId).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public List<ResourceResponse> listResourcesByStatus(ResourceStatus status) {
    log.info("Listing resources by status: {}", status);
    return resourceRepository.findByStatus(status).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public ResourceResponse updateResource(UUID id, CreateResourceRequest request) {
    log.info("Updating resource: id={}", id);
    Resource resource = resourceRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Resource", id));

    if (!resource.getCode().equals(request.code()) &&
        resourceRepository.findByCode(request.code()).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_RESOURCE_CODE", "Resource with code " + request.code() + " already exists");
    }

    ResourceTypeDef def = resolveTypeDef(request.resourceTypeDefId(), request.resourceType());

    resource.setCode(request.code());
    resource.setName(request.name());
    resource.setResourceType(def.getBaseCategory());
    resource.setResourceTypeDef(def);
    resource.setParentId(request.parentId());
    resource.setCalendarId(request.calendarId());
    resource.setEmail(request.email());
    resource.setPhone(request.phone());
    resource.setTitle(request.title());
    if (request.maxUnitsPerDay() != null) {
      resource.setMaxUnitsPerDay(request.maxUnitsPerDay());
    }
    if (request.status() != null) {
      resource.setStatus(request.status());
    }
    if (request.hourlyRate() != null) {
      resource.setHourlyRate(request.hourlyRate());
    }
    if (request.costPerUse() != null) {
      resource.setCostPerUse(request.costPerUse());
    }
    if (request.overtimeRate() != null) {
      resource.setOvertimeRate(request.overtimeRate());
    }
    if (request.unit() != null) {
      resource.setUnit(request.unit());
    }
    if (request.capacitySpec() != null) {
      resource.setCapacitySpec(request.capacitySpec());
    }
    if (request.makeModel() != null) {
      resource.setMakeModel(request.makeModel());
    }
    if (request.quantityAvailable() != null) {
      resource.setQuantityAvailable(request.quantityAvailable());
    }
    if (request.ownershipType() != null) {
      resource.setOwnershipType(request.ownershipType());
    }
    if (request.standardOutputPerDay() != null) {
      resource.setStandardOutputPerDay(request.standardOutputPerDay());
    }
    if (request.standardOutputUnit() != null) {
      resource.setStandardOutputUnit(request.standardOutputUnit());
    }
    if (request.fuelLitresPerHour() != null) {
      resource.setFuelLitresPerHour(request.fuelLitresPerHour());
    }

    Resource updated = resourceRepository.save(resource);
    log.info("Resource updated: id={}", id);

    // Audit log update
    if (!resource.getName().equals(request.name())) {
      auditService.logUpdate("Resource", id, "name", resource.getName(), request.name());
    }

    return ResourceResponse.from(updated);
  }

  /**
   * Auto-generate a code: prefer the def's {@code codePrefix} when set; otherwise fall back to
   * the base category default ({@code LAB} / {@code EQ} / {@code MAT}). Walks the existing
   * {@code code} column to find the next available suffix so multi-process inserts don't collide.
   */
  private String generateResourceCode(ResourceTypeDef def) {
    String prefix = prefixFor(def);
    int next = 1;
    java.util.regex.Pattern p = java.util.regex.Pattern.compile("^" + java.util.regex.Pattern.quote(prefix) + "-(\\d+)$");
    for (Resource r : resourceRepository.findAll()) {
      if (r.getCode() == null) continue;
      java.util.regex.Matcher m = p.matcher(r.getCode());
      if (m.matches()) {
        int n = Integer.parseInt(m.group(1));
        if (n >= next) next = n + 1;
      }
    }
    return String.format("%s-%03d", prefix, next);
  }

  private static String prefixFor(ResourceTypeDef def) {
    if (def.getCodePrefix() != null && !def.getCodePrefix().isBlank()) {
      return def.getCodePrefix();
    }
    return switch (def.getBaseCategory()) {
      case NONLABOR -> "EQ";
      case LABOR -> "LAB";
      case MATERIAL -> "MAT";
    };
  }

  /**
   * Resolve the def from the request: explicit id wins, otherwise look up the seeded system
   * default for the supplied base-category enum (so legacy callers still work).
   */
  private ResourceTypeDef resolveTypeDef(UUID defId, ResourceType baseCategory) {
    if (defId != null) {
      return resourceTypeDefRepository.findById(defId)
          .orElseThrow(() -> new ResourceNotFoundException("ResourceTypeDef", defId));
    }
    if (baseCategory == null) {
      throw new BusinessRuleException("RESOURCE_TYPE_REQUIRED",
          "Either resourceTypeDefId or resourceType must be provided");
    }
    return resourceTypeDefRepository.findFirstByBaseCategoryAndSystemDefaultTrue(baseCategory)
        .orElseThrow(() -> new BusinessRuleException("RESOURCE_TYPE_NOT_SEEDED",
            "No system-default Resource Type for base category " + baseCategory));
  }

  public void deleteResource(UUID id) {
    log.info("Deleting resource: id={}", id);
    if (!resourceRepository.existsById(id)) {
      throw new ResourceNotFoundException("Resource", id);
    }
    resourceRepository.deleteById(id);
    log.info("Resource deleted: id={}", id);

    // Audit log deletion
    auditService.logDelete("Resource", id);
  }
}
