package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.project.application.dto.CreateDailyActivityResourceOutputRequest;
import com.bipros.project.application.dto.DailyActivityResourceOutputResponse;
import com.bipros.project.domain.model.DailyActivityResourceOutput;
import com.bipros.project.domain.repository.DailyActivityResourceOutputRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
public class DailyActivityResourceOutputService {

  /** Default working hours used to derive {@code daysWorked} when only hours are supplied. */
  private static final double DEFAULT_HOURS_PER_DAY = 8.0;

  private final DailyActivityResourceOutputRepository repository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

  /** Used to resolve the activity's {@code work_activity.default_unit} via a tiny native query —
   *  keeps {@code bipros-project} independent of {@code bipros-activity}/{@code bipros-resource}
   *  while still letting the unit auto-fill when blank. */
  @PersistenceContext
  private EntityManager em;

  public DailyActivityResourceOutputResponse create(
      UUID projectId, CreateDailyActivityResourceOutputRequest request) {
    ensureProjectExists(projectId);
    rejectDuplicate(projectId, request.outputDate(), request.activityId(), request.resourceId(), null);

    String unit = request.unit() == null || request.unit().isBlank()
        ? resolveUnitFromActivity(request.activityId())
        : request.unit().trim();
    if (unit == null || unit.isBlank()) {
      throw new BusinessRuleException("UNIT_REQUIRED",
          "unit is required (and the linked activity has no default unit to fall back to)");
    }

    Double daysWorked = request.daysWorked();
    if (daysWorked == null && request.hoursWorked() != null) {
      daysWorked = request.hoursWorked() / DEFAULT_HOURS_PER_DAY;
    }

    DailyActivityResourceOutput row = DailyActivityResourceOutput.builder()
        .projectId(projectId)
        .outputDate(request.outputDate())
        .activityId(request.activityId())
        .resourceId(request.resourceId())
        .qtyExecuted(request.qtyExecuted())
        .unit(unit)
        .hoursWorked(request.hoursWorked())
        .daysWorked(daysWorked)
        .remarks(request.remarks())
        .build();

    DailyActivityResourceOutput saved = repository.save(row);
    auditService.logCreate("DailyActivityResourceOutput", saved.getId(),
        DailyActivityResourceOutputResponse.from(saved));
    log.info("Created DailyActivityResourceOutput id={} project={} date={} activity={} resource={}",
        saved.getId(), projectId, request.outputDate(), request.activityId(), request.resourceId());
    return DailyActivityResourceOutputResponse.from(saved);
  }

  public List<DailyActivityResourceOutputResponse> createBulk(
      UUID projectId, List<CreateDailyActivityResourceOutputRequest> requests) {
    return requests.stream().map(r -> create(projectId, r)).toList();
  }

  @Transactional(readOnly = true)
  public List<DailyActivityResourceOutputResponse> list(
      UUID projectId, LocalDate fromDate, LocalDate toDate, UUID activityId, UUID resourceId) {
    ensureProjectExists(projectId);
    List<DailyActivityResourceOutput> rows;
    if (activityId != null) {
      rows = repository.findByProjectIdAndActivityIdOrderByOutputDateDescIdAsc(projectId, activityId);
    } else if (resourceId != null) {
      rows = repository.findByProjectIdAndResourceIdOrderByOutputDateDescIdAsc(projectId, resourceId);
    } else if (fromDate != null && toDate != null) {
      rows = repository.findByProjectIdAndOutputDateBetweenOrderByOutputDateDescIdAsc(projectId, fromDate, toDate);
    } else {
      rows = repository.findByProjectIdOrderByOutputDateDescIdAsc(projectId);
    }
    return rows.stream().map(DailyActivityResourceOutputResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public DailyActivityResourceOutputResponse get(UUID projectId, UUID id) {
    return DailyActivityResourceOutputResponse.from(find(projectId, id));
  }

  public void delete(UUID projectId, UUID id) {
    DailyActivityResourceOutput row = find(projectId, id);
    repository.delete(row);
    auditService.logDelete("DailyActivityResourceOutput", id);
  }

  private DailyActivityResourceOutput find(UUID projectId, UUID id) {
    DailyActivityResourceOutput row = repository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("DailyActivityResourceOutput", id));
    if (!row.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("DailyActivityResourceOutput", id);
    }
    return row;
  }

  private void rejectDuplicate(UUID projectId, LocalDate outputDate, UUID activityId, UUID resourceId, UUID excludeId) {
    repository
        .findFirstByProjectIdAndOutputDateAndActivityIdAndResourceId(projectId, outputDate, activityId, resourceId)
        .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
        .ifPresent(existing -> {
          throw new BusinessRuleException("DUPLICATE_DAILY_OUTPUT",
              "A daily output already exists for this project + date + activity + resource");
        });
  }

  private void ensureProjectExists(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }

  /**
   * Cross-schema lookup: {@code activity.activities.work_activity_id → resource.work_activities.default_unit}.
   * Returns null when the activity has no master link or the link doesn't resolve.
   */
  private String resolveUnitFromActivity(UUID activityId) {
    try {
      Object result = em.createNativeQuery(
              "SELECT wa.default_unit FROM activity.activities a "
                  + "JOIN resource.work_activities wa ON wa.id = a.work_activity_id "
                  + "WHERE a.id = :activityId")
          .setParameter("activityId", activityId)
          .getSingleResult();
      return result == null ? null : result.toString();
    } catch (Exception ignored) {
      // Activity not found, no work-activity link, or schema not present in dev profile slices.
      return null;
    }
  }
}
