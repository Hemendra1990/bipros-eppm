package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.EmploymentTypeMasterRequest;
import com.bipros.resource.application.dto.EmploymentTypeMasterResponse;
import com.bipros.resource.domain.model.master.EmploymentTypeMaster;
import com.bipros.resource.domain.repository.EmploymentTypeMasterRepository;
import com.bipros.resource.domain.repository.ManpowerMasterRepository;
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
public class EmploymentTypeMasterService {

  private final EmploymentTypeMasterRepository repository;
  private final ManpowerMasterRepository manpowerMasterRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<EmploymentTypeMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(displayOrder())
        .map(EmploymentTypeMasterResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public EmploymentTypeMasterResponse get(UUID id) {
    return EmploymentTypeMasterResponse.from(repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("EmploymentTypeMaster", id)));
  }

  public EmploymentTypeMasterResponse create(EmploymentTypeMasterRequest req) {
    String code = req.code().trim();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_EMPLOYMENT_TYPE_CODE",
          "Employment Type with code " + code + " already exists");
    }
    EmploymentTypeMaster saved = repository.save(EmploymentTypeMaster.builder()
        .code(code)
        .name(req.name().trim())
        .description(req.description())
        .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build());
    EmploymentTypeMasterResponse response = EmploymentTypeMasterResponse.from(saved);
    auditService.logCreate("EmploymentTypeMaster", saved.getId(), response);
    return response;
  }

  public EmploymentTypeMasterResponse update(UUID id, EmploymentTypeMasterRequest req) {
    EmploymentTypeMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("EmploymentTypeMaster", id));
    String code = req.code().trim();
    if (!m.getCode().equals(code) && repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_EMPLOYMENT_TYPE_CODE",
          "Employment Type with code " + code + " already exists");
    }
    m.setCode(code);
    m.setName(req.name().trim());
    m.setDescription(req.description());
    if (req.sortOrder() != null) m.setSortOrder(req.sortOrder());
    if (req.active() != null) m.setActive(req.active());
    EmploymentTypeMasterResponse response = EmploymentTypeMasterResponse.from(repository.save(m));
    auditService.logUpdate("EmploymentTypeMaster", id, "employmentType", null, response);
    return response;
  }

  public void delete(UUID id) {
    EmploymentTypeMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("EmploymentTypeMaster", id));
    long usage = manpowerMasterRepository.countByEmploymentType(m.getName());
    if (usage > 0) {
      throw new BusinessRuleException("EMPLOYMENT_TYPE_IN_USE",
          "Employment Type '" + m.getName() + "' is used by " + usage
              + " manpower resource(s) and cannot be deleted");
    }
    repository.delete(m);
    auditService.logDelete("EmploymentTypeMaster", id);
  }

  private static Comparator<EmploymentTypeMaster> displayOrder() {
    Comparator<EmploymentTypeMaster> bySort = Comparator.comparing(
        EmploymentTypeMaster::getSortOrder,
        Comparator.nullsLast(Comparator.naturalOrder()));
    return bySort.thenComparing(
        EmploymentTypeMaster::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
