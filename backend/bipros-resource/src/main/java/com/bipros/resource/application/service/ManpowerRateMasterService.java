package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.ManpowerRateMasterRequest;
import com.bipros.resource.application.dto.ManpowerRateMasterResponse;
import com.bipros.resource.domain.model.GradeMaster;
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.model.master.ManpowerCategoryMaster;
import com.bipros.resource.domain.model.rate.ManpowerRateMaster;
import com.bipros.resource.domain.repository.GradeMasterRepository;
import com.bipros.resource.domain.repository.ManpowerCategoryMasterRepository;
import com.bipros.resource.domain.repository.ManpowerRateMasterRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
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
public class ManpowerRateMasterService {

  private final ManpowerRateMasterRepository repository;
  private final ResourceRoleRepository roleRepository;
  private final ManpowerCategoryMasterRepository categoryRepository;
  private final GradeMasterRepository gradeRepository;
  private final RateMasterSyncService rateMasterSyncService;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<ManpowerRateMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(Comparator.comparing(ManpowerRateMaster::getCreatedAt,
            Comparator.nullsLast(Comparator.naturalOrder())))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ManpowerRateMasterResponse get(UUID id) {
    return toResponse(require(id));
  }

  public ManpowerRateMasterResponse create(ManpowerRateMasterRequest req) {
    ResourceRole role = requireRole(req.roleId());
    ManpowerCategoryMaster category = requireCategory(req.categoryId());
    GradeMaster grade = requireGrade(req.gradeId());

    repository.findByRoleIdAndCategoryIdAndGradeId(
        req.roleId(), req.categoryId(), req.gradeId())
        .ifPresent(existing -> {
          throw new BusinessRuleException("DUPLICATE_MANPOWER_RATE_KEY",
              "A manpower rate already exists for role '" + role.getName()
                  + "', category '" + category.getName()
                  + "', grade '" + grade.getCode() + "'");
        });

    ManpowerRateMaster entity = ManpowerRateMaster.builder()
        .roleId(req.roleId())
        .categoryId(req.categoryId())
        .gradeId(req.gradeId())
        .unit(req.unit().trim())
        .rate(req.rate())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build();
    ManpowerRateMaster saved = repository.save(entity);
    auditService.logCreate("ManpowerRateMaster", saved.getId(), toResponse(saved));
    return toResponse(saved);
  }

  public ManpowerRateMasterResponse update(UUID id, ManpowerRateMasterRequest req) {
    ManpowerRateMaster e = require(id);
    requireRole(req.roleId());
    requireCategory(req.categoryId());
    requireGrade(req.gradeId());

    boolean keyChanged = !e.getRoleId().equals(req.roleId())
        || !e.getCategoryId().equals(req.categoryId())
        || !e.getGradeId().equals(req.gradeId());

    if (keyChanged) {
      repository.findByRoleIdAndCategoryIdAndGradeId(
          req.roleId(), req.categoryId(), req.gradeId())
          .filter(other -> !other.getId().equals(id))
          .ifPresent(other -> {
            throw new BusinessRuleException("DUPLICATE_MANPOWER_RATE_KEY",
                "Another manpower rate already uses that role/category/grade combination");
          });
      e.setRoleId(req.roleId());
      e.setCategoryId(req.categoryId());
      e.setGradeId(req.gradeId());
    }

    e.setUnit(req.unit().trim());
    e.setRate(req.rate());
    if (req.active() != null) e.setActive(req.active());

    ManpowerRateMaster saved = repository.save(e);
    rateMasterSyncService.syncResourcesForRateMaster(saved.getId(), saved.getUnit(), saved.getRate());
    auditService.logUpdate("ManpowerRateMaster", id, "manpowerRate", null, toResponse(saved));
    return toResponse(saved);
  }

  public void delete(UUID id) {
    ManpowerRateMaster e = require(id);
    repository.delete(e);
    auditService.logDelete("ManpowerRateMaster", id);
  }

  private ManpowerRateMaster require(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ManpowerRateMaster", id));
  }

  private ResourceRole requireRole(UUID id) {
    return roleRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceRole", id));
  }

  private ManpowerCategoryMaster requireCategory(UUID id) {
    ManpowerCategoryMaster cat = categoryRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ManpowerCategoryMaster", id));
    if (cat.getParentId() != null) {
      throw new BusinessRuleException("CATEGORY_MUST_BE_TOP_LEVEL",
          "Category '" + cat.getName() + "' is a sub-category; pick its parent instead");
    }
    return cat;
  }

  private GradeMaster requireGrade(UUID id) {
    return gradeRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("GradeMaster", id));
  }

  private ManpowerRateMasterResponse toResponse(ManpowerRateMaster e) {
    ResourceRole role = roleRepository.findById(e.getRoleId()).orElse(null);
    ManpowerCategoryMaster cat = categoryRepository.findById(e.getCategoryId()).orElse(null);
    GradeMaster grade = gradeRepository.findById(e.getGradeId()).orElse(null);
    return ManpowerRateMasterResponse.of(e,
        role == null ? null : role.getCode(),
        role == null ? null : role.getName(),
        cat == null ? null : cat.getName(),
        grade == null ? null : grade.getCode(),
        grade == null ? null : grade.getName());
  }
}
