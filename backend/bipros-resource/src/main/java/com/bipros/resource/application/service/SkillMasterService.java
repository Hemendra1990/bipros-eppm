package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.SkillMasterRequest;
import com.bipros.resource.application.dto.SkillMasterResponse;
import com.bipros.resource.domain.model.master.SkillMaster;
import com.bipros.resource.domain.repository.SkillMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for the Skill master. In-use check is omitted because skill names are stored as
 * JSON arrays on {@code ManpowerSkills.primarySkill} / {@code secondarySkills}; querying that
 * with a portable count is awkward. Deleting a skill that's still referenced just leaves the
 * resource displaying the saved string — no data corruption.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class SkillMasterService {

  private final SkillMasterRepository repository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<SkillMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(displayOrder())
        .map(SkillMasterResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public SkillMasterResponse get(UUID id) {
    return SkillMasterResponse.from(repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("SkillMaster", id)));
  }

  public SkillMasterResponse create(SkillMasterRequest req) {
    String code = req.code().trim();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_SKILL_CODE",
          "Skill with code " + code + " already exists");
    }
    SkillMaster saved = repository.save(SkillMaster.builder()
        .code(code)
        .name(req.name().trim())
        .description(req.description())
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build());
    SkillMasterResponse response = SkillMasterResponse.from(saved);
    auditService.logCreate("SkillMaster", saved.getId(), response);
    return response;
  }

  public SkillMasterResponse update(UUID id, SkillMasterRequest req) {
    SkillMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("SkillMaster", id));
    String code = req.code().trim();
    if (!m.getCode().equals(code) && repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_SKILL_CODE",
          "Skill with code " + code + " already exists");
    }
    m.setCode(code);
    m.setName(req.name().trim());
    m.setDescription(req.description());
    if (req.sortOrder() != null) m.setSortOrder(req.sortOrder());
    if (req.active() != null) m.setActive(req.active());
    SkillMasterResponse response = SkillMasterResponse.from(repository.save(m));
    auditService.logUpdate("SkillMaster", id, "skill", null, response);
    return response;
  }

  public void delete(UUID id) {
    SkillMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("SkillMaster", id));
    repository.delete(m);
    auditService.logDelete("SkillMaster", id);
  }

  private static Comparator<SkillMaster> displayOrder() {
    Comparator<SkillMaster> bySort = Comparator.comparing(
        SkillMaster::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(
        SkillMaster::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
