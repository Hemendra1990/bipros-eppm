package com.bipros.project.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDailyResourceDeploymentRequest;
import com.bipros.project.application.dto.DailyResourceDeploymentResponse;
import com.bipros.project.domain.model.DailyResourceDeployment;
import com.bipros.project.domain.model.DeploymentResourceType;
import com.bipros.project.domain.repository.DailyResourceDeploymentRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DailyResourceDeploymentService {

  private final DailyResourceDeploymentRepository repository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

  public DailyResourceDeploymentResponse create(UUID projectId, CreateDailyResourceDeploymentRequest request) {
    ensureProjectExists(projectId);

    DailyResourceDeployment deployment = DailyResourceDeployment.builder()
        .projectId(projectId)
        .logDate(request.logDate())
        .resourceType(request.resourceType())
        .resourceDescription(request.resourceDescription())
        .resourceId(request.resourceId())
        .resourceRoleId(request.resourceRoleId())
        .nosPlanned(request.nosPlanned())
        .nosDeployed(request.nosDeployed())
        .hoursWorked(request.hoursWorked())
        .idleHours(request.idleHours())
        .remarks(request.remarks())
        .build();

    DailyResourceDeployment saved = repository.save(deployment);
    auditService.logCreate("DailyResourceDeployment", saved.getId(), DailyResourceDeploymentResponse.from(saved));
    return DailyResourceDeploymentResponse.from(saved);
  }

  public List<DailyResourceDeploymentResponse> createBulk(UUID projectId, List<CreateDailyResourceDeploymentRequest> requests) {
    return requests.stream().map(r -> create(projectId, r)).toList();
  }

  @Transactional(readOnly = true)
  public List<DailyResourceDeploymentResponse> list(
      UUID projectId, LocalDate from, LocalDate to, DeploymentResourceType resourceType) {
    ensureProjectExists(projectId);
    List<DailyResourceDeployment> rows;
    if (from != null && to != null) {
      rows = repository.findByProjectIdAndLogDateBetweenOrderByLogDateAscIdAsc(projectId, from, to);
    } else {
      rows = repository.findByProjectIdOrderByLogDateAscIdAsc(projectId);
    }
    if (resourceType != null) {
      rows = rows.stream().filter(r -> r.getResourceType() == resourceType).toList();
    }
    return rows.stream().map(DailyResourceDeploymentResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public DailyResourceDeploymentResponse get(UUID projectId, UUID id) {
    return DailyResourceDeploymentResponse.from(find(projectId, id));
  }

  public void delete(UUID projectId, UUID id) {
    DailyResourceDeployment deployment = find(projectId, id);
    repository.delete(deployment);
    auditService.logDelete("DailyResourceDeployment", id);
  }

  private DailyResourceDeployment find(UUID projectId, UUID id) {
    DailyResourceDeployment deployment = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("DailyResourceDeployment", id));
    if (!deployment.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("DailyResourceDeployment", id);
    }
    return deployment;
  }

  private void ensureProjectExists(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }
}
