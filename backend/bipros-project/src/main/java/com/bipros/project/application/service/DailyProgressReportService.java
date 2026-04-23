package com.bipros.project.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDailyProgressReportRequest;
import com.bipros.project.application.dto.DailyProgressReportResponse;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DailyProgressReportService {

  private final DailyProgressReportRepository dprRepository;
  private final ProjectRepository projectRepository;
  private final BoqService boqService;
  private final AuditService auditService;

  public DailyProgressReportResponse create(UUID projectId, CreateDailyProgressReportRequest request) {
    ensureProjectExists(projectId);

    DailyProgressReport dpr = DailyProgressReport.builder()
        .projectId(projectId)
        .reportDate(request.reportDate())
        .supervisorName(request.supervisorName())
        .chainageFromM(request.chainageFromM())
        .chainageToM(request.chainageToM())
        .activityName(request.activityName())
        .wbsNodeId(request.wbsNodeId())
        .boqItemNo(request.boqItemNo())
        .unit(request.unit())
        .qtyExecuted(request.qtyExecuted())
        .weatherCondition(request.weatherCondition())
        .remarks(request.remarks())
        .build();

    dpr.setCumulativeQty(calculateCumulative(projectId, request.activityName(), request.qtyExecuted()));

    DailyProgressReport saved = dprRepository.save(dpr);

    // Optional BOQ sync — keeps the planning artifact in lockstep with site reality.
    if (request.boqItemNo() != null && !request.boqItemNo().isBlank()) {
      boqService.addExecutedQty(projectId, request.boqItemNo(), request.qtyExecuted());
    }

    auditService.logCreate("DailyProgressReport", saved.getId(), DailyProgressReportResponse.from(saved));
    return DailyProgressReportResponse.from(saved);
  }

  public List<DailyProgressReportResponse> createBulk(UUID projectId, List<CreateDailyProgressReportRequest> requests) {
    // One-at-a-time so the cumulativeQty / BOQ sync ordering is deterministic on bulk seed.
    return requests.stream().map(r -> create(projectId, r)).toList();
  }

  @Transactional(readOnly = true)
  public List<DailyProgressReportResponse> list(UUID projectId, LocalDate from, LocalDate to, String activityName) {
    ensureProjectExists(projectId);
    List<DailyProgressReport> rows;
    if (activityName != null && !activityName.isBlank()) {
      rows = dprRepository.findByProjectIdAndActivityNameIgnoreCaseOrderByReportDateAsc(projectId, activityName);
    } else if (from != null && to != null) {
      rows = dprRepository.findByProjectIdAndReportDateBetweenOrderByReportDateAscIdAsc(projectId, from, to);
    } else {
      rows = dprRepository.findByProjectIdOrderByReportDateAscIdAsc(projectId);
    }
    return rows.stream().map(DailyProgressReportResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public DailyProgressReportResponse get(UUID projectId, UUID id) {
    DailyProgressReport dpr = find(projectId, id);
    return DailyProgressReportResponse.from(dpr);
  }

  public void delete(UUID projectId, UUID id) {
    DailyProgressReport dpr = find(projectId, id);
    dprRepository.delete(dpr);
    auditService.logDelete("DailyProgressReport", id);
  }

  private BigDecimal calculateCumulative(UUID projectId, String activityName, BigDecimal qtyToday) {
    // Sum prior executions + today's qty. Small ROI, low-volume table so full fetch is fine.
    BigDecimal prior = dprRepository
        .findByProjectIdAndActivityNameIgnoreCaseOrderByReportDateAsc(projectId, activityName)
        .stream()
        .map(DailyProgressReport::getQtyExecuted)
        .filter(q -> q != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return prior.add(qtyToday != null ? qtyToday : BigDecimal.ZERO);
  }

  private DailyProgressReport find(UUID projectId, UUID id) {
    DailyProgressReport dpr = dprRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("DailyProgressReport", id));
    if (!dpr.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("DailyProgressReport", id);
    }
    return dpr;
  }

  private void ensureProjectExists(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }
}
