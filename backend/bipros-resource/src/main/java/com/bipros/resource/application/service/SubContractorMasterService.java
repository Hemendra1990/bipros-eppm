package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.SubContractorMasterRequest;
import com.bipros.resource.application.dto.SubContractorMasterResponse;
import com.bipros.resource.domain.model.master.SubContractorMaster;
import com.bipros.resource.domain.repository.ActivitySubContractorAssignmentRepository;
import com.bipros.resource.domain.repository.SubContractorMasterRepository;
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
public class SubContractorMasterService {

  private final SubContractorMasterRepository repository;
  private final ActivitySubContractorAssignmentRepository assignmentRepository;
  private final AuditService auditService;

  @Transactional(readOnly = true)
  public List<SubContractorMasterResponse> list() {
    return repository.findAll().stream()
        .sorted(displayOrder())
        .map(SubContractorMasterResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public SubContractorMasterResponse get(UUID id) {
    return SubContractorMasterResponse.from(repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("SubContractorMaster", id)));
  }

  public SubContractorMasterResponse create(SubContractorMasterRequest req) {
    String code = req.code().trim();
    if (repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_SUB_CONTRACTOR_CODE",
          "Sub-Contractor with code " + code + " already exists");
    }
    SubContractorMaster saved = repository.save(SubContractorMaster.builder()
        .code(code)
        .name(req.name().trim())
        .location(req.location())
        .primaryContactName(req.primaryContactName())
        .primaryContactNumber(req.primaryContactNumber())
        .active(req.active() == null ? Boolean.TRUE : req.active())
        .build());
    SubContractorMasterResponse response = SubContractorMasterResponse.from(saved);
    auditService.logCreate("SubContractorMaster", saved.getId(), response);
    return response;
  }

  public SubContractorMasterResponse update(UUID id, SubContractorMasterRequest req) {
    SubContractorMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("SubContractorMaster", id));
    String code = req.code().trim();
    if (!m.getCode().equals(code) && repository.findByCode(code).isPresent()) {
      throw new BusinessRuleException("DUPLICATE_SUB_CONTRACTOR_CODE",
          "Sub-Contractor with code " + code + " already exists");
    }
    m.setCode(code);
    m.setName(req.name().trim());
    m.setLocation(req.location());
    m.setPrimaryContactName(req.primaryContactName());
    m.setPrimaryContactNumber(req.primaryContactNumber());
    if (req.active() != null) m.setActive(req.active());
    SubContractorMasterResponse response = SubContractorMasterResponse.from(repository.save(m));
    auditService.logUpdate("SubContractorMaster", id, "subContractor", null, response);
    return response;
  }

  public void delete(UUID id) {
    SubContractorMaster m = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("SubContractorMaster", id));
    long usage = assignmentRepository.countBySubContractorMasterId(id);
    if (usage > 0) {
      throw new BusinessRuleException("SUB_CONTRACTOR_IN_USE",
          "Sub-Contractor '" + m.getName() + "' is referenced by " + usage
              + " activity assignment(s) and cannot be deleted");
    }
    repository.delete(m);
    auditService.logDelete("SubContractorMaster", id);
  }

  private static Comparator<SubContractorMaster> displayOrder() {
    Comparator<SubContractorMaster> byActive = Comparator.comparing(
        SubContractorMaster::getActive,
        Comparator.nullsLast(Comparator.reverseOrder()));
    return byActive.thenComparing(
        SubContractorMaster::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }
}
