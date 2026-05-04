package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.ResourceRoleRequest;
import com.bipros.resource.application.dto.ResourceRoleResponse;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ResourceRoleService {

  private final ResourceRoleRepository roleRepository;
  private final ResourceTypeRepository typeRepository;
  private final ResourceRepository resourceRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<ResourceRoleResponse> list() {
    return roleRepository.findAll().stream()
        .sorted(displayOrder())
        .map(ResourceRoleResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ResourceRoleResponse> listByType(UUID typeId) {
    return roleRepository.findByResourceType_Id(typeId).stream()
        .sorted(displayOrder())
        .map(ResourceRoleResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ResourceRoleResponse get(UUID id) {
    ResourceRole r = roleRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceRole", id));
    return ResourceRoleResponse.from(r);
  }

  public ResourceRoleResponse create(ResourceRoleRequest req) {
    ResourceType type = typeRepository.findById(req.resourceTypeId())
        .orElseThrow(() -> new ResourceNotFoundException("ResourceType", req.resourceTypeId()));

    String code = req.code().trim();
    if (roleRepository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_ROLE_CODE",
          "Role with code " + code + " already exists");
    }

    ResourceRole r = ResourceRole.builder()
        .code(code)
        .name(req.name())
        .description(req.description())
        .resourceType(type)
        .productivityUnit(req.productivityUnit())
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build();

    ResourceRole saved = roleRepository.save(r);
    auditService.logCreate("ResourceRole", saved.getId(), ResourceRoleResponse.from(saved));
    return ResourceRoleResponse.from(saved);
  }

  public ResourceRoleResponse update(UUID id, ResourceRoleRequest req) {
    ResourceRole r = roleRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceRole", id));

    ResourceType type = typeRepository.findById(req.resourceTypeId())
        .orElseThrow(() -> new ResourceNotFoundException("ResourceType", req.resourceTypeId()));

    String code = req.code().trim();
    if (!r.getCode().equals(code) && roleRepository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_ROLE_CODE",
          "Role with code " + code + " already exists");
    }

    r.setCode(code);
    r.setName(req.name());
    r.setDescription(req.description());
    r.setResourceType(type);
    r.setProductivityUnit(req.productivityUnit());
    if (req.sortOrder() != null) r.setSortOrder(req.sortOrder());
    if (req.active() != null) r.setActive(req.active());

    ResourceRole saved = roleRepository.save(r);
    auditService.logUpdate("ResourceRole", id, "resourceRole", null, ResourceRoleResponse.from(saved));
    return ResourceRoleResponse.from(saved);
  }

  public void delete(UUID id) {
    ResourceRole r = roleRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceRole", id));

    long usage = resourceRepository.countByRole_Id(id);
    if (usage > 0) {
      throw new BusinessRuleException("RESOURCE_ROLE_IN_USE",
          "Resource Role '" + r.getName() + "' is used by " + usage
              + " resource(s) and cannot be deleted");
    }

    roleRepository.delete(r);
    auditService.logDelete("ResourceRole", id);
  }

  private static Comparator<ResourceRole> displayOrder() {
    Comparator<ResourceRole> bySort = Comparator.comparing(
        ResourceRole::getSortOrder,
        Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(ResourceRole::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
