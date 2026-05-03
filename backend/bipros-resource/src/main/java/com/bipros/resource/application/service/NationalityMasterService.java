package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.NationalityMasterRequest;
import com.bipros.resource.application.dto.NationalityMasterResponse;
import com.bipros.resource.domain.model.master.NationalityMaster;
import com.bipros.resource.domain.repository.ManpowerMasterRepository;
import com.bipros.resource.domain.repository.NationalityMasterRepository;
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
public class NationalityMasterService {

  private final NationalityMasterRepository repository;
  private final ManpowerMasterRepository manpowerMasterRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<NationalityMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(displayOrder())
        .map(NationalityMasterResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public NationalityMasterResponse get(UUID id) {
    return NationalityMasterResponse.from(repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("NationalityMaster", id)));
  }

  public NationalityMasterResponse create(NationalityMasterRequest req) {
    String code = req.code().trim();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_NATIONALITY_CODE",
          "Nationality with code " + code + " already exists");
    }
    NationalityMaster saved = repository.save(NationalityMaster.builder()
        .code(code)
        .name(req.name().trim())
        .description(req.description())
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build());
    NationalityMasterResponse response = NationalityMasterResponse.from(saved);
    auditService.logCreate("NationalityMaster", saved.getId(), response);
    return response;
  }

  public NationalityMasterResponse update(UUID id, NationalityMasterRequest req) {
    NationalityMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("NationalityMaster", id));
    String code = req.code().trim();
    if (!m.getCode().equals(code) && repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_NATIONALITY_CODE",
          "Nationality with code " + code + " already exists");
    }
    m.setCode(code);
    m.setName(req.name().trim());
    m.setDescription(req.description());
    if (req.sortOrder() != null) m.setSortOrder(req.sortOrder());
    if (req.active() != null) m.setActive(req.active());
    NationalityMasterResponse response = NationalityMasterResponse.from(repository.save(m));
    auditService.logUpdate("NationalityMaster", id, "nationality", null, response);
    return response;
  }

  public void delete(UUID id) {
    NationalityMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("NationalityMaster", id));
    long usage = manpowerMasterRepository.countByNationality(m.getName());
    if (usage > 0) {
      throw new BusinessRuleException("NATIONALITY_IN_USE",
          "Nationality '" + m.getName() + "' is used by " + usage
              + " manpower resource(s) and cannot be deleted");
    }
    repository.delete(m);
    auditService.logDelete("NationalityMaster", id);
  }

  private static Comparator<NationalityMaster> displayOrder() {
    Comparator<NationalityMaster> bySort = Comparator.comparing(
        NationalityMaster::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(
        NationalityMaster::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
