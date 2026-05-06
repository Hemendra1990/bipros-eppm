package com.bipros.project.application.service;

import com.bipros.common.event.DprMutationType;
import com.bipros.common.event.DprSubmittedEvent;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDailyProgressReportRequest;
import com.bipros.project.application.dto.DailyProgressReportResponse;
import com.bipros.project.application.dto.UpdateDailyProgressReportRequest;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DPR mutation flow:
 * <ul>
 *   <li>Service writes the row, publishes a {@link DprSubmittedEvent} carrying the change
 *       type plus old/new BOQ qty + item linkage.</li>
 *   <li>{@code DprBoqSyncListener} (in-module) reacts to update {@code BoqItem.qtyExecutedToDate}
 *       transactionally with the DPR write.</li>
 *   <li>{@code DprSubmittedListener} (analytics) reacts AFTER_COMMIT to write ClickHouse fact rows.</li>
 * </ul>
 * Cumulative qty is never stored — list/get computes it on read so back-dated edits stay
 * self-consistent without rewriting later rows.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DailyProgressReportService {

  private final DailyProgressReportRepository dprRepository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public DailyProgressReportResponse create(UUID projectId, CreateDailyProgressReportRequest request) {
    ensureProjectExists(projectId);

    DailyProgressReport dpr = DailyProgressReport.builder()
        .projectId(projectId)
        .reportDate(request.reportDate())
        .supervisorResourceId(request.supervisorResourceId())
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

    DailyProgressReport saved = dprRepository.save(dpr);

    BigDecimal cumulative = computeCumulative(saved.getProjectId(), saved.getActivityName(), saved.getReportDate());
    DailyProgressReportResponse response = DailyProgressReportResponse.from(saved, cumulative);

    auditService.logCreate("DailyProgressReport", saved.getId(), response);
    eventPublisher.publishEvent(new DprSubmittedEvent(
        saved.getProjectId(),
        saved.getId(),
        saved.getReportDate(),
        saved.getActivityName(),
        saved.getBoqItemNo(),
        saved.getQtyExecuted(),
        null,
        null,
        DprMutationType.CREATED));
    return response;
  }

  public List<DailyProgressReportResponse> createBulk(UUID projectId, List<CreateDailyProgressReportRequest> requests) {
    // One-at-a-time so the BOQ sync listener fires deterministically per row on bulk seed.
    return requests.stream().map(r -> create(projectId, r)).toList();
  }

  public DailyProgressReportResponse update(UUID projectId, UUID id, UpdateDailyProgressReportRequest request) {
    DailyProgressReport dpr = find(projectId, id);

    String oldBoqItemNo = dpr.getBoqItemNo();
    BigDecimal oldQty = dpr.getQtyExecuted();
    DailyProgressReportResponse before = DailyProgressReportResponse.from(dpr,
        computeCumulative(dpr.getProjectId(), dpr.getActivityName(), dpr.getReportDate()));

    dpr.setReportDate(request.reportDate());
    dpr.setSupervisorResourceId(request.supervisorResourceId());
    dpr.setSupervisorName(request.supervisorName());
    dpr.setChainageFromM(request.chainageFromM());
    dpr.setChainageToM(request.chainageToM());
    dpr.setActivityName(request.activityName());
    dpr.setWbsNodeId(request.wbsNodeId());
    dpr.setBoqItemNo(request.boqItemNo());
    dpr.setUnit(request.unit());
    dpr.setQtyExecuted(request.qtyExecuted());
    dpr.setWeatherCondition(request.weatherCondition());
    dpr.setRemarks(request.remarks());

    DailyProgressReport saved = dprRepository.save(dpr);

    BigDecimal cumulative = computeCumulative(saved.getProjectId(), saved.getActivityName(), saved.getReportDate());
    DailyProgressReportResponse after = DailyProgressReportResponse.from(saved, cumulative);

    auditService.logUpdate("DailyProgressReport", saved.getId(), "row", before, after);
    eventPublisher.publishEvent(new DprSubmittedEvent(
        saved.getProjectId(),
        saved.getId(),
        saved.getReportDate(),
        saved.getActivityName(),
        saved.getBoqItemNo(),
        saved.getQtyExecuted(),
        oldBoqItemNo,
        oldQty,
        DprMutationType.UPDATED));
    return after;
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
    return attachComputedCumulative(rows);
  }

  @Transactional(readOnly = true)
  public DailyProgressReportResponse get(UUID projectId, UUID id) {
    DailyProgressReport dpr = find(projectId, id);
    BigDecimal cumulative = computeCumulative(dpr.getProjectId(), dpr.getActivityName(), dpr.getReportDate());
    return DailyProgressReportResponse.from(dpr, cumulative);
  }

  public void delete(UUID projectId, UUID id) {
    DailyProgressReport dpr = find(projectId, id);
    String oldBoqItemNo = dpr.getBoqItemNo();
    BigDecimal oldQty = dpr.getQtyExecuted();
    UUID dprId = dpr.getId();
    LocalDate reportDate = dpr.getReportDate();
    String activityName = dpr.getActivityName();

    dprRepository.delete(dpr);
    auditService.logDelete("DailyProgressReport", id);

    eventPublisher.publishEvent(new DprSubmittedEvent(
        projectId,
        dprId,
        reportDate,
        activityName,
        null,
        null,
        oldBoqItemNo,
        oldQty,
        DprMutationType.DELETED));
  }

  /**
   * Sum of qtyExecuted for the (project, activityName) up to and including the given date.
   * Equivalent to the legacy stored {@code cumulativeQty} but always fresh.
   */
  private BigDecimal computeCumulative(UUID projectId, String activityName, LocalDate reportDate) {
    BigDecimal sum = dprRepository.sumQtyExecutedThroughDate(projectId, activityName, reportDate);
    return sum != null ? sum : BigDecimal.ZERO;
  }

  /**
   * Walks rows in date order per (project, activityName), accumulating cumulative locally
   * to avoid an N+1 query. Single pass per response.
   */
  private List<DailyProgressReportResponse> attachComputedCumulative(List<DailyProgressReport> rows) {
    Map<String, BigDecimal> running = new HashMap<>();
    List<DailyProgressReportResponse> out = new ArrayList<>(rows.size());
    // Source query orders by reportDate asc then id asc — within a (project, activity) we accumulate.
    rows.stream()
        .sorted((a, b) -> {
          int byDate = a.getReportDate().compareTo(b.getReportDate());
          if (byDate != 0) return byDate;
          return a.getId().compareTo(b.getId());
        })
        .forEach(r -> {
          String key = r.getProjectId() + "::" + (r.getActivityName() == null ? "" : r.getActivityName().toLowerCase());
          BigDecimal cumulative = running.getOrDefault(key, BigDecimal.ZERO)
              .add(r.getQtyExecuted() != null ? r.getQtyExecuted() : BigDecimal.ZERO);
          running.put(key, cumulative);
          out.add(DailyProgressReportResponse.from(r, cumulative));
        });
    return out;
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
