package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.GradeMasterRequest;
import com.bipros.resource.application.dto.GradeMasterResponse;
import com.bipros.resource.domain.model.GradeMaster;
import com.bipros.resource.domain.repository.GradeMasterRepository;
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
public class GradeMasterService {

  private final GradeMasterRepository repository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<GradeMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(displayOrder())
        .map(GradeMasterResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public GradeMasterResponse get(UUID id) {
    GradeMaster e = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("GradeMaster", id));
    return GradeMasterResponse.from(e);
  }

  public GradeMasterResponse create(GradeMasterRequest req) {
    String code = req.code().trim().toUpperCase();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_GRADE_CODE",
          "Grade with code " + code + " already exists");
    }
    GradeMaster e = GradeMaster.builder()
        .code(code)
        .name(req.name())
        .description(req.description())
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build();
    GradeMaster saved = repository.save(e);
    auditService.logCreate("GradeMaster", saved.getId(), GradeMasterResponse.from(saved));
    return GradeMasterResponse.from(saved);
  }

  public GradeMasterResponse update(UUID id, GradeMasterRequest req) {
    GradeMaster e = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("GradeMaster", id));

    String requestedCode = req.code() == null ? null : req.code().trim().toUpperCase();
    if (requestedCode != null && !requestedCode.equals(e.getCode())) {
      if (repository.findByCode(requestedCode).isPresent()) {
        throw new BusinessRuleException("DUPLICATE_GRADE_CODE",
            "Grade with code " + requestedCode + " already exists");
      }
      e.setCode(requestedCode);
    }

    e.setName(req.name());
    e.setDescription(req.description());
    if (req.sortOrder() != null) e.setSortOrder(req.sortOrder());
    if (req.active() != null) e.setActive(req.active());

    GradeMaster saved = repository.save(e);
    auditService.logUpdate("GradeMaster", id, "grade", null, GradeMasterResponse.from(saved));
    return GradeMasterResponse.from(saved);
  }

  public void delete(UUID id) {
    GradeMaster e = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("GradeMaster", id));
    repository.delete(e);
    auditService.logDelete("GradeMaster", id);
  }

  /** Used by rate master services and seeders to fetch the entity without DTO mapping. */
  @Transactional(readOnly = true)
  public GradeMaster requireById(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("GradeMaster", id));
  }

  private static Comparator<GradeMaster> displayOrder() {
    Comparator<GradeMaster> bySort = Comparator.comparing(
        GradeMaster::getSortOrder,
        Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(GradeMaster::getCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
