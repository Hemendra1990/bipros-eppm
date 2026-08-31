package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.MaterialCategoryMasterRequest;
import com.bipros.resource.application.dto.MaterialCategoryMasterResponse;
import com.bipros.resource.domain.model.MaterialCategoryMaster;
import com.bipros.resource.domain.repository.MaterialCategoryMasterRepository;
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
public class MaterialCategoryMasterService {

  private final MaterialCategoryMasterRepository repository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<MaterialCategoryMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(displayOrder())
        .map(MaterialCategoryMasterResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public MaterialCategoryMasterResponse get(UUID id) {
    MaterialCategoryMaster e = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("MaterialCategoryMaster", id));
    return MaterialCategoryMasterResponse.from(e);
  }

  public MaterialCategoryMasterResponse create(MaterialCategoryMasterRequest req) {
    String code = req.code().trim().toUpperCase();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_MATERIAL_CATEGORY_CODE",
          "Material category with code " + code + " already exists");
    }
    MaterialCategoryMaster e = MaterialCategoryMaster.builder()
        .code(code)
        .name(req.name())
        .description(req.description())
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build();
    MaterialCategoryMaster saved = repository.save(e);
    auditService.logCreate("MaterialCategoryMaster", saved.getId(), MaterialCategoryMasterResponse.from(saved));
    return MaterialCategoryMasterResponse.from(saved);
  }

  public MaterialCategoryMasterResponse update(UUID id, MaterialCategoryMasterRequest req) {
    MaterialCategoryMaster e = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("MaterialCategoryMaster", id));

    String requestedCode = req.code() == null ? null : req.code().trim().toUpperCase();
    if (requestedCode != null && !requestedCode.equals(e.getCode())) {
      if (repository.findByCode(requestedCode).isPresent()) {
        throw new BusinessRuleException("DUPLICATE_MATERIAL_CATEGORY_CODE",
            "Material category with code " + requestedCode + " already exists");
      }
      e.setCode(requestedCode);
    }

    e.setName(req.name());
    e.setDescription(req.description());
    if (req.sortOrder() != null) e.setSortOrder(req.sortOrder());
    if (req.active() != null) e.setActive(req.active());

    MaterialCategoryMaster saved = repository.save(e);
    auditService.logUpdate("MaterialCategoryMaster", id, "materialCategory", null, MaterialCategoryMasterResponse.from(saved));
    return MaterialCategoryMasterResponse.from(saved);
  }

  public void delete(UUID id) {
    MaterialCategoryMaster e = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("MaterialCategoryMaster", id));
    repository.delete(e);
    auditService.logDelete("MaterialCategoryMaster", id);
  }

  /** Used by rate master services and seeders to fetch the entity without DTO mapping. */
  @Transactional(readOnly = true)
  public MaterialCategoryMaster requireById(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("MaterialCategoryMaster", id));
  }

  private static Comparator<MaterialCategoryMaster> displayOrder() {
    Comparator<MaterialCategoryMaster> bySort = Comparator.comparing(
        MaterialCategoryMaster::getSortOrder,
        Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(MaterialCategoryMaster::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
