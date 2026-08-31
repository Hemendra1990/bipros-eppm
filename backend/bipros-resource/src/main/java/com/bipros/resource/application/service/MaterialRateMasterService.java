package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.MaterialRateMasterRequest;
import com.bipros.resource.application.dto.MaterialRateMasterResponse;
import com.bipros.resource.domain.model.MaterialCategoryMaster;
import com.bipros.resource.domain.model.rate.MaterialRateMaster;
import com.bipros.resource.domain.repository.MaterialCategoryMasterRepository;
import com.bipros.resource.domain.repository.MaterialRateMasterRepository;
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
public class MaterialRateMasterService {

  private final MaterialRateMasterRepository repository;
  private final MaterialCategoryMasterRepository categoryRepository;
  private final RateMasterSyncService rateMasterSyncService;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<MaterialRateMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(Comparator.comparing(MaterialRateMaster::getCreatedAt,
            Comparator.nullsLast(Comparator.naturalOrder())))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public MaterialRateMasterResponse get(UUID id) {
    return toResponse(require(id));
  }

  public MaterialRateMasterResponse create(MaterialRateMasterRequest req) {
    MaterialCategoryMaster category = requireCategory(req.categoryId());
    String specGrade = req.specGrade().trim();

    repository.findByCategoryIdAndSpecGrade(req.categoryId(), specGrade)
        .ifPresent(existing -> {
          throw new BusinessRuleException("DUPLICATE_MATERIAL_RATE_KEY",
              "A material rate already exists for '" + category.getName()
                  + " / " + specGrade + "'");
        });

    MaterialRateMaster entity = MaterialRateMaster.builder()
        .categoryId(req.categoryId())
        .specGrade(specGrade)
        .unit(req.unit().trim())
        .rate(req.rate())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build();
    MaterialRateMaster saved = repository.save(entity);
    auditService.logCreate("MaterialRateMaster", saved.getId(), toResponse(saved));
    return toResponse(saved);
  }

  public MaterialRateMasterResponse update(UUID id, MaterialRateMasterRequest req) {
    MaterialRateMaster e = require(id);
    requireCategory(req.categoryId());
    String specGrade = req.specGrade().trim();

    boolean keyChanged = !e.getCategoryId().equals(req.categoryId())
        || !e.getSpecGrade().equals(specGrade);

    if (keyChanged) {
      repository.findByCategoryIdAndSpecGrade(req.categoryId(), specGrade)
          .filter(other -> !other.getId().equals(id))
          .ifPresent(other -> {
            throw new BusinessRuleException("DUPLICATE_MATERIAL_RATE_KEY",
                "Another material rate already uses that category and spec/grade");
          });
      e.setCategoryId(req.categoryId());
      e.setSpecGrade(specGrade);
    }

    e.setUnit(req.unit().trim());
    e.setRate(req.rate());
    if (req.active() != null) e.setActive(req.active());

    MaterialRateMaster saved = repository.save(e);
    rateMasterSyncService.syncResourcesForRateMaster(saved.getId(), saved.getUnit(), saved.getRate());
    auditService.logUpdate("MaterialRateMaster", id, "materialRate", null, toResponse(saved));
    return toResponse(saved);
  }

  public void delete(UUID id) {
    MaterialRateMaster e = require(id);
    repository.delete(e);
    auditService.logDelete("MaterialRateMaster", id);
  }

  private MaterialRateMaster require(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("MaterialRateMaster", id));
  }

  private MaterialCategoryMaster requireCategory(UUID id) {
    return categoryRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("MaterialCategoryMaster", id));
  }

  private MaterialRateMasterResponse toResponse(MaterialRateMaster e) {
    MaterialCategoryMaster cat = categoryRepository.findById(e.getCategoryId()).orElse(null);
    return MaterialRateMasterResponse.of(e,
        cat == null ? null : cat.getCode(),
        cat == null ? null : cat.getName());
  }
}
