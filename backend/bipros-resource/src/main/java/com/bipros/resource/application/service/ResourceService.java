package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateResourceRequest;
import com.bipros.resource.application.dto.EquipmentDetailsDto;
import com.bipros.resource.application.dto.ManpowerDto;
import com.bipros.resource.application.dto.MaterialDetailsDto;
import com.bipros.resource.application.dto.ResourceResponse;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeRepository;
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
  private final ResourceRoleRepository roleRepository;
  private final ResourceTypeRepository typeRepository;
  private final EquipmentDetailsService equipmentDetailsService;
  private final MaterialDetailsService materialDetailsService;
  private final ManpowerService manpowerService;
  private final AuditService auditService;

  // ─── Code constants ───
  private static final String TYPE_LABOR = "LABOR";
  private static final String TYPE_EQUIPMENT = "EQUIPMENT";
  private static final String TYPE_MATERIAL = "MATERIAL";

  public ResourceResponse createResource(CreateResourceRequest request) {
    log.info("Creating resource: code={}, roleId={}, typeId={}",
        request.code(), request.roleId(), request.resourceTypeId());

    ResourceType type = typeRepository.findById(request.resourceTypeId())
        .orElseThrow(() -> new ResourceNotFoundException("ResourceType", request.resourceTypeId()));
    ResourceRole role = roleRepository.findById(request.roleId())
        .orElseThrow(() -> new ResourceNotFoundException("ResourceRole", request.roleId()));

    if (role.getResourceType() != null && !role.getResourceType().getId().equals(type.getId())) {
      throw new BusinessRuleException("ROLE_TYPE_MISMATCH",
          "ResourceRole '" + role.getCode() + "' belongs to type '"
              + (role.getResourceType().getCode()) + "' but the request specifies type '"
              + type.getCode() + "'");
    }

    validateDetailExclusivity(type.getCode(), request);

    String code = (request.code() != null && !request.code().isBlank())
        ? request.code() : generateResourceCode(type);
    if (resourceRepository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_RESOURCE_CODE",
          "Resource with code " + code + " already exists");
    }

    Resource resource = Resource.builder()
        .code(code)
        .name(request.name())
        .description(request.description())
        .role(role)
        .resourceType(type)
        .availability(request.availability())
        .costPerUnit(request.costPerUnit())
        .unit(request.unit())
        .status(request.status() == null ? ResourceStatus.ACTIVE : request.status())
        .calendarId(request.calendarId())
        .parentId(request.parentId())
        .userId(request.userId())
        .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
        .build();

    Resource saved = resourceRepository.save(resource);
    log.info("Resource created: id={}", saved.getId());

    persistDetailSection(saved.getId(), type.getCode(), request);

    auditService.logCreate("Resource", saved.getId(), ResourceResponse.from(saved));
    return loadFull(saved);
  }

  @Transactional(readOnly = true)
  public ResourceResponse getResource(UUID id) {
    Resource resource = resourceRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Resource", id));
    return loadFull(resource);
  }

  @Transactional(readOnly = true)
  public List<ResourceResponse> listResources() {
    return resourceRepository.findAll().stream()
        .map(ResourceResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ResourceResponse> listResourcesByType(String typeCode) {
    return resourceRepository.findByResourceType_Code(typeCode).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ResourceResponse> listResourcesByRole(UUID roleId) {
    return resourceRepository.findByRole_Id(roleId).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ResourceResponse> listResourceHierarchyRoots() {
    return resourceRepository.findByParentIdIsNull().stream()
        .map(ResourceResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ResourceResponse> listResourcesByParent(UUID parentId) {
    return resourceRepository.findByParentId(parentId).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ResourceResponse> listResourcesByStatus(ResourceStatus status) {
    return resourceRepository.findByStatus(status).stream()
        .map(ResourceResponse::from)
        .toList();
  }

  public ResourceResponse updateResource(UUID id, CreateResourceRequest request) {
    Resource resource = resourceRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Resource", id));

    ResourceType type = typeRepository.findById(request.resourceTypeId())
        .orElseThrow(() -> new ResourceNotFoundException("ResourceType", request.resourceTypeId()));
    ResourceRole role = roleRepository.findById(request.roleId())
        .orElseThrow(() -> new ResourceNotFoundException("ResourceRole", request.roleId()));

    if (role.getResourceType() != null && !role.getResourceType().getId().equals(type.getId())) {
      throw new BusinessRuleException("ROLE_TYPE_MISMATCH",
          "ResourceRole '" + role.getCode() + "' belongs to type '"
              + (role.getResourceType().getCode()) + "' but the request specifies type '"
              + type.getCode() + "'");
    }

    validateDetailExclusivity(type.getCode(), request);

    String code = (request.code() != null && !request.code().isBlank())
        ? request.code() : resource.getCode();
    if (!resource.getCode().equals(code) && resourceRepository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_RESOURCE_CODE",
          "Resource with code " + code + " already exists");
    }

    resource.setCode(code);
    resource.setName(request.name());
    resource.setDescription(request.description());
    resource.setRole(role);
    resource.setResourceType(type);
    if (request.availability() != null) resource.setAvailability(request.availability());
    if (request.costPerUnit() != null) resource.setCostPerUnit(request.costPerUnit());
    if (request.unit() != null) resource.setUnit(request.unit());
    if (request.status() != null) resource.setStatus(request.status());
    resource.setCalendarId(request.calendarId());
    resource.setParentId(request.parentId());
    resource.setUserId(request.userId());
    if (request.sortOrder() != null) resource.setSortOrder(request.sortOrder());

    Resource updated = resourceRepository.save(resource);

    persistDetailSection(updated.getId(), type.getCode(), request);

    auditService.logUpdate("Resource", id, "resource", null, ResourceResponse.from(updated));
    return loadFull(updated);
  }

  public void deleteResource(UUID id) {
    if (!resourceRepository.existsById(id)) {
      throw new ResourceNotFoundException("Resource", id);
    }
    // DB-level ON DELETE CASCADE handles the per-type detail rows.
    resourceRepository.deleteById(id);
    auditService.logDelete("Resource", id);
  }

  public void deleteAllResources() {
    long count = resourceRepository.count();
    resourceRepository.deleteAllInBatch();
    log.info("Deleted {} resources", count);
  }

  // ─── helpers ───

  /**
   * Reject mismatched detail sections: equipment fields only on EQUIPMENT-typed resources, etc.
   * This prevents accidentally writing material data onto a labor row in cross-type clients.
   */
  private void validateDetailExclusivity(String typeCode, CreateResourceRequest req) {
    String code = typeCode == null ? null : typeCode.toUpperCase();
    if (req.equipment() != null && !TYPE_EQUIPMENT.equals(code)) {
      throw new BusinessRuleException("EQUIPMENT_DETAILS_NOT_ALLOWED",
          "Equipment details are only allowed on EQUIPMENT-typed resources");
    }
    if (req.material() != null && !TYPE_MATERIAL.equals(code)) {
      throw new BusinessRuleException("MATERIAL_DETAILS_NOT_ALLOWED",
          "Material details are only allowed on MATERIAL-typed resources");
    }
    if (req.manpower() != null && !TYPE_LABOR.equals(code)) {
      throw new BusinessRuleException("MANPOWER_DETAILS_NOT_ALLOWED",
          "Manpower details are only allowed on LABOR-typed resources");
    }
  }

  private void persistDetailSection(UUID resourceId, String typeCode, CreateResourceRequest req) {
    String code = typeCode == null ? null : typeCode.toUpperCase();
    if (TYPE_EQUIPMENT.equals(code) && req.equipment() != null) {
      equipmentDetailsService.upsert(resourceId, req.equipment());
    } else if (TYPE_MATERIAL.equals(code) && req.material() != null) {
      materialDetailsService.upsert(resourceId, req.material());
    } else if (TYPE_LABOR.equals(code) && req.manpower() != null) {
      manpowerService.upsertAll(resourceId, req.manpower());
    }
  }

  private ResourceResponse loadFull(Resource r) {
    String code = r.getResourceType() == null ? null : r.getResourceType().getCode();
    EquipmentDetailsDto equipment = null;
    MaterialDetailsDto material = null;
    ManpowerDto manpower = null;
    if (TYPE_EQUIPMENT.equalsIgnoreCase(code)) {
      equipment = equipmentDetailsService.get(r.getId());
    } else if (TYPE_MATERIAL.equalsIgnoreCase(code)) {
      material = materialDetailsService.get(r.getId());
    } else if (TYPE_LABOR.equalsIgnoreCase(code)) {
      manpower = manpowerService.get(r.getId());
    }
    return ResourceResponse.from(r, equipment, material, manpower);
  }

  /** Auto-generated code "LAB-001" / "EQ-001" / "MAT-001" depending on type code. */
  private String generateResourceCode(ResourceType type) {
    String prefix = prefixFor(type);
    int next = 1;
    java.util.regex.Pattern p = java.util.regex.Pattern.compile(
        "^" + java.util.regex.Pattern.quote(prefix) + "-(\\d+)$");
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

  private static String prefixFor(ResourceType type) {
    if (type == null || type.getCode() == null) return "RES";
    return switch (type.getCode().toUpperCase()) {
      case TYPE_LABOR -> "LAB";
      case TYPE_EQUIPMENT -> "EQ";
      case TYPE_MATERIAL -> "MAT";
      default -> type.getCode().toUpperCase();
    };
  }
}
