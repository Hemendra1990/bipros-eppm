package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.ManpowerCategoryMasterRequest;
import com.bipros.resource.application.dto.ManpowerCategoryMasterResponse;
import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;
import com.bipros.resource.domain.repository.ManpowerCategoryMasterRepository;
import com.bipros.resource.domain.repository.ManpowerMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD for the Manpower Category Master. Supports the parent-child hierarchy:
 * top-level Categories (parentId = null) and Sub-Categories (parentId = some Category id).
 *
 * <p>Values stored on {@code ManpowerMaster.category} / {@code ManpowerMaster.subCategory} are
 * the master's {@code name} (string). The in-use delete check counts by that name so users can't
 * delete a Category still referenced by a resource. Sub-categories are also blocked from deletion
 * when their parent has children.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ManpowerCategoryMasterService {

  private final ManpowerCategoryMasterRepository repository;
  private final ManpowerMasterRepository manpowerMasterRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<ManpowerCategoryMasterResponse> list() {
    List<ManpowerCategoryMaster> all = repository.findAll();
    Map<UUID, String> nameById = new HashMap<>();
    for (ManpowerCategoryMaster m : all) nameById.put(m.getId(), m.getName());
    return all.stream()
        .sorted(displayOrder())
        .map(m -> ManpowerCategoryMasterResponse.from(m,
            m.getParentId() == null ? null : nameById.get(m.getParentId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ManpowerCategoryMasterResponse> listTopLevel() {
    return repository.findByParentIdIsNull().stream()
        .sorted(displayOrder())
        .map(m -> ManpowerCategoryMasterResponse.from(m, null))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ManpowerCategoryMasterResponse> listByParent(UUID parentId) {
    String parentName = repository.findById(parentId)
        .map(ManpowerCategoryMaster::getName)
        .orElseThrow(() -> new ResourceNotFoundException("ManpowerCategoryMaster", parentId));
    return repository.findByParentId(parentId).stream()
        .sorted(displayOrder())
        .map(m -> ManpowerCategoryMasterResponse.from(m, parentName))
        .toList();
  }

  @Transactional(readOnly = true)
  public ManpowerCategoryMasterResponse get(UUID id) {
    ManpowerCategoryMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ManpowerCategoryMaster", id));
    String parentName = m.getParentId() == null ? null
        : repository.findById(m.getParentId()).map(ManpowerCategoryMaster::getName).orElse(null);
    return ManpowerCategoryMasterResponse.from(m, parentName);
  }

  public ManpowerCategoryMasterResponse create(ManpowerCategoryMasterRequest req) {
    String code = req.code().trim();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_CATEGORY_CODE",
          "Category with code " + code + " already exists");
    }
    if (req.parentId() != null && !repository.existsById(req.parentId())) {
      throw new ResourceNotFoundException("ManpowerCategoryMaster (parent)", req.parentId());
    }

    ManpowerCategoryMaster m = ManpowerCategoryMaster.builder()
        .code(code)
        .name(req.name().trim())
        .description(req.description())
        .parentId(req.parentId())
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build();

    ManpowerCategoryMaster saved = repository.save(m);
    String parentName = saved.getParentId() == null ? null
        : repository.findById(saved.getParentId()).map(ManpowerCategoryMaster::getName).orElse(null);
    ManpowerCategoryMasterResponse response = ManpowerCategoryMasterResponse.from(saved, parentName);
    auditService.logCreate("ManpowerCategoryMaster", saved.getId(), response);
    return response;
  }

  public ManpowerCategoryMasterResponse update(UUID id, ManpowerCategoryMasterRequest req) {
    ManpowerCategoryMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ManpowerCategoryMaster", id));

    String code = req.code().trim();
    if (!m.getCode().equals(code) && repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_CATEGORY_CODE",
          "Category with code " + code + " already exists");
    }
    if (req.parentId() != null) {
      if (req.parentId().equals(id)) {
        throw new BusinessRuleException("INVALID_PARENT",
            "A category cannot be its own parent");
      }
      if (!repository.existsById(req.parentId())) {
        throw new ResourceNotFoundException("ManpowerCategoryMaster (parent)", req.parentId());
      }
    }

    m.setCode(code);
    m.setName(req.name().trim());
    m.setDescription(req.description());
    m.setParentId(req.parentId());
    if (req.sortOrder() != null) m.setSortOrder(req.sortOrder());
    if (req.active() != null) m.setActive(req.active());

    ManpowerCategoryMaster saved = repository.save(m);
    String parentName = saved.getParentId() == null ? null
        : repository.findById(saved.getParentId()).map(ManpowerCategoryMaster::getName).orElse(null);
    ManpowerCategoryMasterResponse response = ManpowerCategoryMasterResponse.from(saved, parentName);
    auditService.logUpdate("ManpowerCategoryMaster", id, "category", null, response);
    return response;
  }

  public void delete(UUID id) {
    ManpowerCategoryMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ManpowerCategoryMaster", id));

    long children = repository.countByParentId(id);
    if (children > 0) {
      throw new BusinessRuleException("CATEGORY_HAS_CHILDREN",
          "Category '" + m.getName() + "' has " + children
              + " sub-category(ies) — delete or reassign them first");
    }

    long usage = m.getParentId() == null
        ? manpowerMasterRepository.countByCategory(m.getName())
        : manpowerMasterRepository.countBySubCategory(m.getName());
    if (usage > 0) {
      throw new BusinessRuleException("CATEGORY_IN_USE",
          (m.getParentId() == null ? "Category '" : "Sub-Category '") + m.getName()
              + "' is used by " + usage + " manpower resource(s) and cannot be deleted");
    }

    repository.delete(m);
    auditService.logDelete("ManpowerCategoryMaster", id);
  }

  private static Comparator<ManpowerCategoryMaster> displayOrder() {
    Comparator<ManpowerCategoryMaster> bySort = Comparator.comparing(
        ManpowerCategoryMaster::getSortOrder,
        Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(
        ManpowerCategoryMaster::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
