package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.application.dto.CreateMaterialConsumptionLogRequest;
import com.bipros.resource.application.dto.MaterialConsumptionLogResponse;
import com.bipros.resource.domain.model.MaterialConsumptionLog;
import com.bipros.resource.domain.repository.MaterialConsumptionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MaterialConsumptionLogService {

  private final MaterialConsumptionLogRepository repository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

  public MaterialConsumptionLogResponse create(
      UUID projectId, CreateMaterialConsumptionLogRequest request) {
    log.info(
        "Creating material consumption log: projectId={}, logDate={}, material={}",
        projectId,
        request.logDate(),
        request.materialName());

    if (projectId == null) {
      throw new BusinessRuleException("PROJECT_ID_REQUIRED", "projectId is required");
    }
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }

    BigDecimal opening = request.openingStock();
    BigDecimal received = request.received() != null ? request.received() : BigDecimal.ZERO;
    BigDecimal consumed = request.consumed() != null ? request.consumed() : BigDecimal.ZERO;
    BigDecimal closing = opening.add(received).subtract(consumed);

    MaterialConsumptionLog entity =
        MaterialConsumptionLog.builder()
            .projectId(projectId)
            .logDate(request.logDate())
            .resourceId(request.resourceId())
            .materialName(request.materialName())
            .unit(request.unit())
            .openingStock(opening)
            .received(received)
            .consumed(consumed)
            .closingStock(closing)
            .wastagePercent(request.wastagePercent())
            .issuedBy(request.issuedBy())
            .receivedBy(request.receivedBy())
            .wbsNodeId(request.wbsNodeId())
            .remarks(request.remarks())
            .build();

    MaterialConsumptionLog saved = repository.save(entity);
    log.info("Material consumption log created: id={}", saved.getId());

    auditService.logCreate(
        "MaterialConsumptionLog", saved.getId(), MaterialConsumptionLogResponse.from(saved));

    return MaterialConsumptionLogResponse.from(saved);
  }

  public List<MaterialConsumptionLogResponse> createBulk(
      UUID projectId, List<CreateMaterialConsumptionLogRequest> requests) {
    log.info(
        "Bulk creating material consumption logs: projectId={}, count={}",
        projectId,
        requests != null ? requests.size() : 0);

    List<MaterialConsumptionLogResponse> results = new ArrayList<>();
    if (requests == null || requests.isEmpty()) {
      return results;
    }
    for (CreateMaterialConsumptionLogRequest req : requests) {
      results.add(create(projectId, req));
    }
    return results;
  }

  @Transactional(readOnly = true)
  public List<MaterialConsumptionLogResponse> list(
      UUID projectId, LocalDate from, LocalDate to) {
    log.info(
        "Listing material consumption logs: projectId={}, from={}, to={}", projectId, from, to);

    List<MaterialConsumptionLog> entities;
    if (from != null && to != null) {
      entities =
          repository.findByProjectIdAndLogDateBetweenOrderByLogDateAscIdAsc(projectId, from, to);
    } else {
      entities = repository.findByProjectIdOrderByLogDateAscIdAsc(projectId);
    }
    return entities.stream().map(MaterialConsumptionLogResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public MaterialConsumptionLogResponse get(UUID projectId, UUID id) {
    log.info("Fetching material consumption log: projectId={}, id={}", projectId, id);
    MaterialConsumptionLog entity =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("MaterialConsumptionLog", id));
    if (!entity.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("MaterialConsumptionLog", id);
    }
    return MaterialConsumptionLogResponse.from(entity);
  }

  public void delete(UUID projectId, UUID id) {
    log.info("Deleting material consumption log: projectId={}, id={}", projectId, id);
    MaterialConsumptionLog entity =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("MaterialConsumptionLog", id));
    if (!entity.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("MaterialConsumptionLog", id);
    }
    repository.delete(entity);
    auditService.logDelete("MaterialConsumptionLog", id);
  }
}
