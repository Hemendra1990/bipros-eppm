package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.SkillLevelMasterRequest;
import com.bipros.resource.application.dto.SkillLevelMasterResponse;
import com.bipros.resource.domain.model.master.SkillLevelMaster;
import com.bipros.resource.domain.repository.ManpowerSkillsRepository;
import com.bipros.resource.domain.repository.SkillLevelMasterRepository;
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
public class SkillLevelMasterService {

  private final SkillLevelMasterRepository repository;
  private final ManpowerSkillsRepository manpowerSkillsRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<SkillLevelMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(displayOrder())
        .map(SkillLevelMasterResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public SkillLevelMasterResponse get(UUID id) {
    return SkillLevelMasterResponse.from(repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("SkillLevelMaster", id)));
  }

  public SkillLevelMasterResponse create(SkillLevelMasterRequest req) {
    String code = req.code().trim();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_SKILL_LEVEL_CODE",
          "Skill Level with code " + code + " already exists");
    }
    SkillLevelMaster saved = repository.save(SkillLevelMaster.builder()
        .code(code)
        .name(req.name().trim())
        .description(req.description())
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build());
    SkillLevelMasterResponse response = SkillLevelMasterResponse.from(saved);
    auditService.logCreate("SkillLevelMaster", saved.getId(), response);
    return response;
  }

  public SkillLevelMasterResponse update(UUID id, SkillLevelMasterRequest req) {
    SkillLevelMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("SkillLevelMaster", id));
    String code = req.code().trim();
    if (!m.getCode().equals(code) && repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_SKILL_LEVEL_CODE",
          "Skill Level with code " + code + " already exists");
    }
    m.setCode(code);
    m.setName(req.name().trim());
    m.setDescription(req.description());
    if (req.sortOrder() != null) m.setSortOrder(req.sortOrder());
    if (req.active() != null) m.setActive(req.active());
    SkillLevelMasterResponse response = SkillLevelMasterResponse.from(repository.save(m));
    auditService.logUpdate("SkillLevelMaster", id, "skillLevel", null, response);
    return response;
  }

  public void delete(UUID id) {
    SkillLevelMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("SkillLevelMaster", id));
    long usage = manpowerSkillsRepository.countBySkillLevel(m.getName());
    if (usage > 0) {
      throw new BusinessRuleException("SKILL_LEVEL_IN_USE",
          "Skill Level '" + m.getName() + "' is used by " + usage
              + " manpower resource(s) and cannot be deleted");
    }
    repository.delete(m);
    auditService.logDelete("SkillLevelMaster", id);
  }

  private static Comparator<SkillLevelMaster> displayOrder() {
    Comparator<SkillLevelMaster> bySort = Comparator.comparing(
        SkillLevelMaster::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(
        SkillLevelMaster::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
