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

    if (request.plannedStartDate() != null
        && request.plannedFinishDate() != null
        && request.plannedFinishDate().isBefore(request.plannedStartDate())) {
      throw new BusinessRuleException(
          "INVALID_DATE_RANGE",
          "plannedFinishDate must be on or after plannedStartDate");
    }

    boolean isMilestone = request.activityType() != null
        && (request.activityType() == com.bipros.activity.domain.model.ActivityType.START_MILESTONE
            || request.activityType() == com.bipros.activity.domain.model.ActivityType.FINISH_MILESTONE);

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

    // Milestones collapse to a point — plannedFinish := plannedStart. For START_MILESTONE, this
    // is the start date; for FINISH_MILESTONE, the finish date if supplied wins.
    LocalDate plannedStart = request.plannedStartDate();
    LocalDate plannedFinish = request.plannedFinishDate();
    if (isMilestone) {
      if (request.activityType() == com.bipros.activity.domain.model.ActivityType.FINISH_MILESTONE
          && plannedFinish != null) {
        plannedStart = plannedFinish;
      } else if (plannedStart != null) {
        plannedFinish = plannedStart;
      } else if (plannedFinish != null) {
        plannedStart = plannedFinish;
      }
    }
    activity.setPlannedStartDate(plannedStart);
    activity.setPlannedFinishDate(plannedFinish);
    activity.setCalendarId(request.calendarId());
    activity.setChainageFromM(request.chainageFromM());
    activity.setChainageToM(request.chainageToM());
    activity.setPercentComplete(0.0);

    Double duration;
    if (isMilestone) {
      // Milestones have zero duration; silently normalise any caller-supplied value.
      duration = 0.0;
    } else {
      duration = request.originalDuration();
      if (duration == null && plannedStart != null && plannedFinish != null) {
        duration = (double) java.time.temporal.ChronoUnit.DAYS.between(plannedStart, plannedFinish);
      }
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

    if (request.name() != null) {
      activity.setName(request.name());
    }
    if (request.description() != null) {
      activity.setDescription(request.description());
    }

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
    boolean statusExplicit = request.status() != null;
    if (statusExplicit) {
      activity.setStatus(request.status());
    }

    if (request.originalDuration() != null) {
      activity.setOriginalDuration(request.originalDuration());
    }
    if (request.remainingDuration() != null) {
      activity.setRemainingDuration(request.remainingDuration());
    }
    boolean progressChanged = request.percentComplete() != null
        || request.actualStartDate() != null
        || request.actualFinishDate() != null;
    if (request.percentComplete() != null) {
      activity.setPercentComplete(request.percentComplete());
    }
    if (request.physicalPercentComplete() != null) {
      activity.setPhysicalPercentComplete(request.physicalPercentComplete());
    }
    if (request.actualStartDate() != null) {
      activity.setActualStartDate(request.actualStartDate());
    }
    if (request.actualFinishDate() != null) {
      activity.setActualFinishDate(request.actualFinishDate());
    }

    // When progress changed but status wasn't explicitly set, derive status from the
    // same progress/actual-date signals that /progress uses.
    if (!statusExplicit && progressChanged) {
      applyStatusFromProgress(activity);
    }
    if (request.calendarId() != null) {
      activity.setCalendarId(request.calendarId());
    }
    if (request.primaryConstraintType() != null) {
      activity.setPrimaryConstraintType(request.primaryConstraintType());
    }
    if (request.primaryConstraintDate() != null) {
      activity.setPrimaryConstraintDate(request.primaryConstraintDate());
    }
    if (request.secondaryConstraintType() != null) {
      activity.setSecondaryConstraintType(request.secondaryConstraintType());
    }
    if (request.secondaryConstraintDate() != null) {
      activity.setSecondaryConstraintDate(request.secondaryConstraintDate());
    }
    if (request.suspendDate() != null) {
      activity.setSuspendDate(request.suspendDate());
    }
    if (request.resumeDate() != null) {
      activity.setResumeDate(request.resumeDate());
    }
    if (request.notes() != null) {
      activity.setNotes(request.notes());
    }
    if (request.chainageFromM() != null) {
      activity.setChainageFromM(request.chainageFromM());
    }
    if (request.chainageToM() != null) {
      activity.setChainageToM(request.chainageToM());
    }

    // Enforce date-order across the planned window after any updates
    LocalDate ps = activity.getPlannedStartDate();
    LocalDate pf = activity.getPlannedFinishDate();
    if (ps != null && pf != null && pf.isBefore(ps)) {
      throw new BusinessRuleException(
          "INVALID_DATE_RANGE",
          "plannedFinishDate must be on or after plannedStartDate");
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
    var oldStatus = activity.getStatus();

    activity.setPercentComplete(percentComplete);
    activity.setActualStartDate(actualStart);
    activity.setActualFinishDate(actualFinish);
    applyStatusFromProgress(activity);

    Activity updated = activityRepository.save(activity);
    log.info("Progress updated successfully: id={}", id);

    if (!java.util.Objects.equals(oldStatus, updated.getStatus())) {
      auditService.logUpdate("Activity", id, "status", oldStatus, updated.getStatus());
    }

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

  /**
   * Derive status from progress. Single source of truth used by both
   * {@link #updateProgress} and {@link #updateActivity} (when the caller
   * hasn't passed an explicit status).
   * <ul>
   *   <li>percentComplete ≥ 100 → COMPLETED</li>
   *   <li>percentComplete &gt; 0 OR actualStartDate set → IN_PROGRESS</li>
   *   <li>otherwise → NOT_STARTED</li>
   * </ul>
   */
  private void applyStatusFromProgress(Activity activity) {
    Double pct = activity.getPercentComplete();
    boolean hasActualStart = activity.getActualStartDate() != null;
    com.bipros.activity.domain.model.ActivityStatus derived;
    if (pct != null && pct >= 100.0) {
      derived = com.bipros.activity.domain.model.ActivityStatus.COMPLETED;
    } else if ((pct != null && pct > 0.0) || hasActualStart) {
      derived = com.bipros.activity.domain.model.ActivityStatus.IN_PROGRESS;
    } else {
      derived = com.bipros.activity.domain.model.ActivityStatus.NOT_STARTED;
    }
    activity.setStatus(derived);
  }
}
