package com.bipros.activity.application.service;

import com.bipros.activity.application.dto.ActivityResponse;
import com.bipros.activity.application.dto.CreateActivityRequest;
import com.bipros.activity.application.dto.UpdateActivityRequest;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ActivityService {

  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository relationshipRepository;
  private final AuditService auditService;

  public ActivityResponse createActivity(CreateActivityRequest request) {
    log.info("Creating activity: code={}, name={}, projectId={}", request.code(), request.name(),
        request.projectId());

    Activity activity = new Activity();
    activity.setCode(request.code());
    activity.setName(request.name());
    activity.setDescription(request.description());
    activity.setProjectId(request.projectId());
    activity.setWbsNodeId(request.wbsNodeId());

    if (request.activityType() != null) {
      activity.setActivityType(request.activityType());
    }
    if (request.durationType() != null) {
      activity.setDurationType(request.durationType());
    }
    if (request.percentCompleteType() != null) {
      activity.setPercentCompleteType(request.percentCompleteType());
    }

    activity.setPlannedStartDate(request.plannedStartDate());
    activity.setPlannedFinishDate(request.plannedFinishDate());
    activity.setCalendarId(request.calendarId());
    activity.setPercentComplete(0.0);

    // Auto-calculate originalDuration from dates if not provided
    Double duration = request.originalDuration();
    if (duration == null && request.plannedStartDate() != null && request.plannedFinishDate() != null) {
      duration = (double) java.time.temporal.ChronoUnit.DAYS.between(
          request.plannedStartDate(), request.plannedFinishDate());
    }
    activity.setOriginalDuration(duration);
    activity.setRemainingDuration(duration);

    Activity saved = activityRepository.save(activity);
    log.info("Activity created successfully: id={}", saved.getId());

    // Audit log creation
    auditService.logCreate("Activity", saved.getId(), ActivityResponse.from(saved));

    return ActivityResponse.from(saved);
  }

  public ActivityResponse updateActivity(UUID id, UpdateActivityRequest request) {
    log.info("Updating activity: id={}", id);

    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));

    // Capture old values for audit BEFORE mutation
    String oldName = activity.getName();
    var oldStatus = activity.getStatus();
    Double oldOriginalDuration = activity.getOriginalDuration();
    Double oldRemainingDuration = activity.getRemainingDuration();
    Double oldPercentComplete = activity.getPercentComplete();
    var oldActualStart = activity.getActualStartDate();
    var oldActualFinish = activity.getActualFinishDate();

    activity.setName(request.name());
    activity.setDescription(request.description());

    if (request.wbsNodeId() != null) {
      activity.setWbsNodeId(request.wbsNodeId());
    }
    if (request.activityType() != null) {
      activity.setActivityType(request.activityType());
    }
    if (request.durationType() != null) {
      activity.setDurationType(request.durationType());
    }
    if (request.percentCompleteType() != null) {
      activity.setPercentCompleteType(request.percentCompleteType());
    }
    if (request.status() != null) {
      activity.setStatus(request.status());
    }

    activity.setOriginalDuration(request.originalDuration());
    activity.setRemainingDuration(request.remainingDuration());
    activity.setPercentComplete(request.percentComplete());
    activity.setPhysicalPercentComplete(request.physicalPercentComplete());
    activity.setActualStartDate(request.actualStartDate());
    activity.setActualFinishDate(request.actualFinishDate());
    activity.setCalendarId(request.calendarId());
    activity.setPrimaryConstraintType(request.primaryConstraintType());
    activity.setPrimaryConstraintDate(request.primaryConstraintDate());
    activity.setSecondaryConstraintType(request.secondaryConstraintType());
    activity.setSecondaryConstraintDate(request.secondaryConstraintDate());
    activity.setSuspendDate(request.suspendDate());
    activity.setResumeDate(request.resumeDate());
    if (request.notes() != null) {
      activity.setNotes(request.notes());
    }

    Activity updated = activityRepository.save(activity);
    log.info("Activity updated successfully: id={}", id);

    // Audit log updates for key fields
    if (request.name() != null && !request.name().equals(oldName)) {
      auditService.logUpdate("Activity", id, "name", oldName, request.name());
    }
    if (request.status() != null && !request.status().equals(oldStatus)) {
      auditService.logUpdate("Activity", id, "status", oldStatus, request.status());
    }
    if (request.originalDuration() != null && !request.originalDuration().equals(oldOriginalDuration)) {
      auditService.logUpdate("Activity", id, "originalDuration", oldOriginalDuration, request.originalDuration());
    }
    if (request.remainingDuration() != null && !request.remainingDuration().equals(oldRemainingDuration)) {
      auditService.logUpdate("Activity", id, "remainingDuration", oldRemainingDuration, request.remainingDuration());
    }
    if (request.percentComplete() != null && !request.percentComplete().equals(oldPercentComplete)) {
      auditService.logUpdate("Activity", id, "percentComplete", oldPercentComplete, request.percentComplete());
    }
    if (request.actualStartDate() != null && !request.actualStartDate().equals(oldActualStart)) {
      auditService.logUpdate("Activity", id, "actualStartDate", oldActualStart, request.actualStartDate());
    }
    if (request.actualFinishDate() != null && !request.actualFinishDate().equals(oldActualFinish)) {
      auditService.logUpdate("Activity", id, "actualFinishDate", oldActualFinish, request.actualFinishDate());
    }

    return ActivityResponse.from(updated);
  }

  public void deleteActivity(UUID id) {
    log.info("Deleting activity: id={}", id);

    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));

    boolean hasRelationships = !relationshipRepository.findByPredecessorActivityId(id).isEmpty()
        || !relationshipRepository.findBySuccessorActivityId(id).isEmpty();

    if (hasRelationships) {
      throw new BusinessRuleException("ACTIVITY_HAS_RELATIONSHIPS",
          "Cannot delete activity with relationships. Remove relationships first.");
    }

    activityRepository.deleteById(id);
    log.info("Activity deleted successfully: id={}", id);

    // Audit log deletion
    auditService.logDelete("Activity", id);
  }

  public ActivityResponse getActivity(UUID id) {
    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
    return ActivityResponse.from(activity);
  }

  public PagedResponse<ActivityResponse> listActivities(UUID projectId, Pageable pageable) {
    log.info("Listing activities for project: projectId={}, page={}, size={}", projectId,
        pageable.getPageNumber(), pageable.getPageSize());

    Page<Activity> page = activityRepository.findByProjectIdOrderBySortOrder(projectId, pageable);
    return PagedResponse.of(
        page.getContent().stream().map(ActivityResponse::from).toList(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize()
    );
  }

  public java.util.List<ActivityResponse> getActivitiesByWbs(UUID wbsNodeId) {
    log.info("Getting activities for WBS node: wbsNodeId={}", wbsNodeId);
    return activityRepository.findByWbsNodeId(wbsNodeId).stream()
        .map(ActivityResponse::from)
        .toList();
  }

  public ActivityResponse updateProgress(UUID id, Double percentComplete, LocalDate actualStart,
      LocalDate actualFinish) {
    log.info("Updating progress for activity: id={}, percentComplete={}", id, percentComplete);

    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));

    if (percentComplete < 0 || percentComplete > 100) {
      throw new BusinessRuleException("INVALID_PERCENT_COMPLETE",
          "Percent complete must be between 0 and 100");
    }

    Double oldPercent = activity.getPercentComplete();
    var oldActualStart = activity.getActualStartDate();
    var oldActualFinish = activity.getActualFinishDate();

    activity.setPercentComplete(percentComplete);
    activity.setActualStartDate(actualStart);
    activity.setActualFinishDate(actualFinish);

    Activity updated = activityRepository.save(activity);
    log.info("Progress updated successfully: id={}", id);

    // Audit progress changes
    if (!java.util.Objects.equals(percentComplete, oldPercent)) {
      auditService.logUpdate("Activity", id, "percentComplete", oldPercent, percentComplete);
    }
    if (!java.util.Objects.equals(actualStart, oldActualStart)) {
      auditService.logUpdate("Activity", id, "actualStartDate", oldActualStart, actualStart);
    }
    if (!java.util.Objects.equals(actualFinish, oldActualFinish)) {
      auditService.logUpdate("Activity", id, "actualFinishDate", oldActualFinish, actualFinish);
    }

    return ActivityResponse.from(updated);
  }

  public void applyActuals(UUID projectId, LocalDate dataDate) {
    log.info("Applying actuals for project: projectId={}, dataDate={}", projectId, dataDate);

    java.util.List<Activity> activities = activityRepository.findByProjectId(projectId);

    for (Activity activity : activities) {
      boolean updated = false;

      if (activity.getPlannedStartDate() != null &&
          activity.getPlannedStartDate().compareTo(dataDate) <= 0 &&
          activity.getActualStartDate() == null) {
        activity.setActualStartDate(activity.getPlannedStartDate());
        updated = true;
      }

      if (activity.getPlannedFinishDate() != null &&
          activity.getPlannedFinishDate().compareTo(dataDate) <= 0 &&
          activity.getActualFinishDate() == null) {
        activity.setActualFinishDate(activity.getPlannedFinishDate());
        activity.setPercentComplete(100.0);
        updated = true;
      } else if (activity.getActualStartDate() != null &&
          activity.getActualFinishDate() == null &&
          activity.getPlannedFinishDate() != null) {
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(
            activity.getActualStartDate(), activity.getPlannedFinishDate());
        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(
            activity.getActualStartDate(), dataDate);

        if (totalDays > 0 && elapsedDays >= 0) {
          double durationPercentComplete = Math.min((double) elapsedDays / totalDays * 100, 100.0);
          activity.setDurationPercentComplete(durationPercentComplete);
          updated = true;
        }
      }

      if (updated) {
        activityRepository.save(activity);
        auditService.logUpdate("Activity", activity.getId(), "applyActuals",
            null, "Auto-applied actuals for dataDate=" + dataDate);
      }
    }

    log.info("Actuals applied successfully for project: projectId={}", projectId);
  }
}
