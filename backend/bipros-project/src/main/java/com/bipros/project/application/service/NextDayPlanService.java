package com.bipros.project.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateNextDayPlanRequest;
import com.bipros.project.application.dto.NextDayPlanResponse;
import com.bipros.project.domain.model.NextDayPlan;
import com.bipros.project.domain.repository.NextDayPlanRepository;
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
public class NextDayPlanService {

  private final NextDayPlanRepository planRepository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

  public NextDayPlanResponse create(UUID projectId, CreateNextDayPlanRequest request) {
    ensureProjectExists(projectId);

    NextDayPlan plan = NextDayPlan.builder()
        .projectId(projectId)
        .reportDate(request.reportDate())
        .nextDayActivity(request.nextDayActivity())
        .chainageFromM(request.chainageFromM())
        .chainageToM(request.chainageToM())
        .targetQty(request.targetQty())
        .unit(request.unit())
        .concerns(request.concerns())
        .actionBy(request.actionBy())
        .dueDate(request.dueDate())
        .build();

    NextDayPlan saved = planRepository.save(plan);
    auditService.logCreate("NextDayPlan", saved.getId(), NextDayPlanResponse.from(saved));
    return NextDayPlanResponse.from(saved);
  }

  public List<NextDayPlanResponse> createBulk(UUID projectId, List<CreateNextDayPlanRequest> requests) {
    return requests.stream().map(r -> create(projectId, r)).toList();
  }

  @Transactional(readOnly = true)
  public List<NextDayPlanResponse> list(UUID projectId, LocalDate from, LocalDate to) {
    ensureProjectExists(projectId);
    List<NextDayPlan> rows;
    if (from != null && to != null) {
      rows = planRepository.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(projectId, from, to);
    } else {
      rows = planRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId);
    }
    return rows.stream().map(NextDayPlanResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public NextDayPlanResponse get(UUID projectId, UUID id) {
    return NextDayPlanResponse.from(find(projectId, id));
  }

  public void delete(UUID projectId, UUID id) {
    NextDayPlan plan = find(projectId, id);
    planRepository.delete(plan);
    auditService.logDelete("NextDayPlan", id);
  }

  private NextDayPlan find(UUID projectId, UUID id) {
    NextDayPlan plan = planRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("NextDayPlan", id));
    if (!plan.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("NextDayPlan", id);
    }
    return plan;
  }

  private void ensureProjectExists(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }
}
