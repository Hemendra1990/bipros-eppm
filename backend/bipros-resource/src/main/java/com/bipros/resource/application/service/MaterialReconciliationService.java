package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateMaterialReconciliationRequest;
import com.bipros.resource.application.dto.MaterialReconciliationResponse;
import com.bipros.resource.domain.model.MaterialReconciliation;
import com.bipros.resource.domain.repository.MaterialReconciliationRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MaterialReconciliationService {

  private final MaterialReconciliationRepository materialReconciliationRepository;
  private final ResourceRepository resourceRepository;
  private final AuditService auditService;

  public MaterialReconciliationResponse createReconciliation(
      CreateMaterialReconciliationRequest request) {
    log.info(
        "Creating material reconciliation: resourceId={}, projectId={}, period={}",
        request.resourceId(),
        request.projectId(),
        request.period());

    resourceRepository
        .findById(request.resourceId())
        .orElseThrow(() -> new ResourceNotFoundException("Resource", request.resourceId()));

    materialReconciliationRepository
        .findByResourceIdAndPeriod(request.resourceId(), request.period())
        .ifPresent(
            existing -> {
              throw new BusinessRuleException(
                  "DUPLICATE_MATERIAL_RECONCILIATION",
                  "Material reconciliation already exists for resource "
                      + request.resourceId()
                      + " and period "
                      + request.period());
            });

    double closing =
        request.openingBalance()
            + request.received()
            - request.consumed()
            - (request.wastage() != null ? request.wastage() : 0.0);

    MaterialReconciliation reconciliation =
        MaterialReconciliation.builder()
            .resourceId(request.resourceId())
            .projectId(request.projectId())
            .wbsNodeId(request.wbsNodeId())
            .period(request.period())
            .openingBalance(request.openingBalance())
            .received(request.received())
            .consumed(request.consumed())
            .wastage(request.wastage() != null ? request.wastage() : 0.0)
            .closingBalance(closing)
            .unit(request.unit())
            .remarks(request.remarks())
            .build();

    MaterialReconciliation saved = materialReconciliationRepository.save(reconciliation);
    log.info("Material reconciliation created: id={}", saved.getId());

    auditService.logCreate(
        "MaterialReconciliation", saved.getId(), MaterialReconciliationResponse.from(saved));

    return MaterialReconciliationResponse.from(saved);
  }

  public List<MaterialReconciliationResponse> getByProject(UUID projectId, String period) {
    log.info("Fetching material reconciliations: projectId={}, period={}", projectId, period);
    return materialReconciliationRepository
        .findByProjectIdAndPeriod(projectId, period)
        .stream()
        .map(MaterialReconciliationResponse::from)
        .toList();
  }

  public List<MaterialReconciliationResponse> getByResource(UUID resourceId) {
    log.info("Fetching material reconciliations by resource: resourceId={}", resourceId);
    return materialReconciliationRepository
        .findByResourceIdOrderByPeriodDesc(resourceId)
        .stream()
        .map(MaterialReconciliationResponse::from)
        .toList();
  }

  public MaterialReconciliationResponse getById(UUID id) {
    log.info("Fetching material reconciliation: id={}", id);
    MaterialReconciliation reconciliation =
        materialReconciliationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("MaterialReconciliation", id));
    return MaterialReconciliationResponse.from(reconciliation);
  }
}
