package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateResourceTypeDefRequest;
import com.bipros.resource.application.dto.ResourceTypeDefResponse;
import com.bipros.resource.application.dto.UpdateResourceTypeDefRequest;
import com.bipros.resource.domain.model.ResourceType;
import com.bipros.resource.domain.model.ResourceTypeDef;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import com.bipros.resource.domain.repository.ResourceTypeDefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ResourceTypeDefService {

  private final ResourceTypeDefRepository repository;
  private final ResourceRepository resourceRepository;
  private final ResourceRoleRepository roleRepository;
  private final AuditService auditService;

  public ResourceTypeDefResponse create(CreateResourceTypeDefRequest request) {
    log.info("Creating resource type def: code={}, baseCategory={}", request.code(), request.baseCategory());

    String code = request.code().trim().toUpperCase();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_RESOURCE_TYPE_CODE",
          "Resource type with code " + code + " already exists");
    }

    ResourceTypeDef def = ResourceTypeDef.builder()
        .code(code)
        .name(request.name())
        .baseCategory(request.baseCategory())
        .codePrefix(normalisePrefix(request.codePrefix()))
        .sortOrder(request.sortOrder())
        .active(request.active() == null ? Boolean.TRUE : request.active())
        .systemDefault(false)
        .build();

    ResourceTypeDef saved = repository.save(def);
    auditService.logCreate("ResourceTypeDef", saved.getId(), ResourceTypeDefResponse.from(saved));
    return ResourceTypeDefResponse.from(saved);
  }

  public ResourceTypeDefResponse update(UUID id, UpdateResourceTypeDefRequest request) {
    log.info("Updating resource type def: id={}", id);
    ResourceTypeDef def = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceTypeDef", id));

    String requestedCode = request.code() == null ? null : request.code().trim().toUpperCase();

    if (Boolean.TRUE.equals(def.getSystemDefault())) {
      if (requestedCode != null && !requestedCode.equals(def.getCode())) {
        throw new BusinessRuleException("SYSTEM_DEFAULT_IMMUTABLE",
            "Cannot change the code of a system-default resource type");
      }
      if (request.baseCategory() != null && request.baseCategory() != def.getBaseCategory()) {
        throw new BusinessRuleException("SYSTEM_DEFAULT_IMMUTABLE",
            "Cannot change the base category of a system-default resource type");
      }
    } else {
      if (requestedCode != null && !requestedCode.equals(def.getCode())
          && repository.findByCode(requestedCode).isPresent()) {
        throw new BusinessRuleException("DUPLICATE_RESOURCE_TYPE_CODE",
            "Resource type with code " + requestedCode + " already exists");
      }
      if (requestedCode != null) {
        def.setCode(requestedCode);
      }
      if (request.baseCategory() != null) {
        def.setBaseCategory(request.baseCategory());
      }
    }

    def.setName(request.name());
    def.setCodePrefix(normalisePrefix(request.codePrefix()));
    def.setSortOrder(request.sortOrder());
    if (request.active() != null) {
      def.setActive(request.active());
    }

    ResourceTypeDef updated = repository.save(def);
    auditService.logUpdate("ResourceTypeDef", id, "resourceTypeDef", null, ResourceTypeDefResponse.from(updated));
    return ResourceTypeDefResponse.from(updated);
  }

  public void delete(UUID id) {
    log.info("Deleting resource type def: id={}", id);
    ResourceTypeDef def = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceTypeDef", id));

    if (Boolean.TRUE.equals(def.getSystemDefault())) {
      throw new BusinessRuleException("SYSTEM_DEFAULT_PROTECTED",
          "Cannot delete the system-default resource type '" + def.getName() + "'");
    }

    long resourceUsage = resourceRepository.countByResourceTypeDefId(id);
    long roleUsage = roleRepository.countByResourceTypeDefId(id);
    long total = resourceUsage + roleUsage;
    if (total > 0) {
      throw new BusinessRuleException("RESOURCE_TYPE_IN_USE",
          "Resource type '" + def.getName() + "' is used by " + total
              + " record(s) and cannot be deleted");
    }

    repository.delete(def);
    auditService.logDelete("ResourceTypeDef", id);
  }

  @Transactional(readOnly = true)
  public ResourceTypeDefResponse get(UUID id) {
    ResourceTypeDef def = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceTypeDef", id));
    return ResourceTypeDefResponse.from(def);
  }

  @Transactional(readOnly = true)
  public List<ResourceTypeDefResponse> list(Boolean activeOnly, ResourceType baseCategory) {
    List<ResourceTypeDef> defs;
    if (baseCategory != null) {
      defs = repository.findByBaseCategory(baseCategory);
    } else if (Boolean.TRUE.equals(activeOnly)) {
      defs = repository.findByActive(Boolean.TRUE);
    } else {
      defs = repository.findAll();
    }
    return defs.stream()
        .filter(d -> activeOnly == null || !activeOnly || Boolean.TRUE.equals(d.getActive()))
        .sorted(displayOrder())
        .map(ResourceTypeDefResponse::from)
        .toList();
  }

  /** Resolve the seeded system-default for a given base category. Used by importers + seeders. */
  @Transactional(readOnly = true)
  public Optional<ResourceTypeDef> findSystemDefault(ResourceType baseCategory) {
    return repository.findFirstByBaseCategoryAndSystemDefaultTrue(baseCategory);
  }

  /** Look up by id without DTO mapping — used by other services to wire defs onto resources. */
  @Transactional(readOnly = true)
  public ResourceTypeDef requireById(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceTypeDef", id));
  }

  private static Comparator<ResourceTypeDef> displayOrder() {
    Comparator<ResourceTypeDef> bySort = Comparator.comparing(
        ResourceTypeDef::getSortOrder,
        Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(ResourceTypeDef::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }

  private static String normalisePrefix(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    return trimmed.isEmpty() ? null : trimmed.toUpperCase();
  }
}
