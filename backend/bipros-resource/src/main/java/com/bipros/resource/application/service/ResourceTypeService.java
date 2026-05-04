package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.ResourceTypeRequest;
import com.bipros.resource.application.dto.ResourceTypeResponse;
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
public class ResourceTypeService {

  private final ResourceTypeRepository repository;
  private final ResourceRoleRepository roleRepository;
  private final ResourceRepository resourceRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<ResourceTypeResponse> list() {
    return repository.findAll().stream()
        .sorted(displayOrder())
        .map(ResourceTypeResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ResourceTypeResponse get(UUID id) {
    ResourceType e = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceType", id));
    return ResourceTypeResponse.from(e);
  }

  public ResourceTypeResponse create(ResourceTypeRequest req) {
    String code = req.code().trim().toUpperCase();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_RESOURCE_TYPE_CODE",
          "Resource type with code " + code + " already exists");
    }
    ResourceType e = ResourceType.builder()
        .code(code)
        .name(req.name())
        .description(req.description())
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .systemDefault(false)
        .build();
    ResourceType saved = repository.save(e);
    auditService.logCreate("ResourceType", saved.getId(), ResourceTypeResponse.from(saved));
    return ResourceTypeResponse.from(saved);
  }

  public ResourceTypeResponse update(UUID id, ResourceTypeRequest req) {
    ResourceType e = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceType", id));

    String requestedCode = req.code() == null ? null : req.code().trim().toUpperCase();

    if (Boolean.TRUE.equals(e.getSystemDefault())) {
      if (requestedCode != null && !requestedCode.equals(e.getCode())) {
        throw new BusinessRuleException("SYSTEM_DEFAULT_IMMUTABLE",
            "Cannot change the code of a system-default resource type");
      }
    } else if (requestedCode != null && !requestedCode.equals(e.getCode())) {
      if (repository.findByCode(requestedCode).isPresent()) {
        throw new BusinessRuleException("DUPLICATE_RESOURCE_TYPE_CODE",
            "Resource type with code " + requestedCode + " already exists");
      }
      e.setCode(requestedCode);
    }

    e.setName(req.name());
    e.setDescription(req.description());
    if (req.sortOrder() != null) e.setSortOrder(req.sortOrder());
    if (req.active() != null) e.setActive(req.active());

    ResourceType saved = repository.save(e);
    auditService.logUpdate("ResourceType", id, "resourceType", null, ResourceTypeResponse.from(saved));
    return ResourceTypeResponse.from(saved);
  }

  public void delete(UUID id) {
    ResourceType e = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceType", id));

    if (Boolean.TRUE.equals(e.getSystemDefault())) {
      throw new BusinessRuleException("SYSTEM_DEFAULT_PROTECTED",
          "Cannot delete the system-default resource type '" + e.getName() + "'");
    }

    long resourceUsage = resourceRepository.countByResourceType_Id(id);
    long roleUsage = roleRepository.countByResourceType_Id(id);
    long total = resourceUsage + roleUsage;
    if (total > 0) {
      throw new BusinessRuleException("RESOURCE_TYPE_IN_USE",
          "Resource type '" + e.getName() + "' is used by " + total
              + " record(s) and cannot be deleted");
    }

    repository.delete(e);
    auditService.logDelete("ResourceType", id);
  }

  /** Used by ResourceService and seeders to fetch the entity without DTO mapping. */
  @Transactional(readOnly = true)
  public ResourceType requireById(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceType", id));
  }

  private static Comparator<ResourceType> displayOrder() {
    Comparator<ResourceType> bySort = Comparator.comparing(
        ResourceType::getSortOrder,
        Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(ResourceType::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
