package com.bipros.activity.application.service;

import com.bipros.activity.application.dto.ActivityResponse;
import com.bipros.activity.application.dto.CreateActivityRequest;
import com.bipros.activity.application.dto.UpdateActivityRequest;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.dto.PagedResponse;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.AccessSpecifications;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ActivityService {

  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository relationshipRepository;
  private final AuditService auditService;
  private final ProjectAccessGuard projectAccess;
  private final ProjectRepository projectRepository;

  public ActivityResponse createActivity(CreateActivityRequest request) {
    log.info("Creating activity: code={}, name={}, projectId={}", request.code(), request.name(),
        request.projectId());

    projectAccess.requireEdit(request.projectId());

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
    UUID calendarId = resolveCalendarId(request.projectId(), request.calendarId());
    activity.setCalendarId(calendarId);
    activity.setChainageFromM(request.chainageFromM());
    activity.setChainageToM(request.chainageToM());
    activity.setWorkActivityId(request.workActivityId());
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

    // Activity-level ABAC: TEAM_MEMBER assignees may update their own activities even
    // without project-edit rights; everyone else must clear projectAccess.requireEdit.
    UUID currentUserId = projectAccess.currentUserId();
    boolean isAssignee = currentUserId != null
        && currentUserId.equals(activity.getAssignedTo());
    if (!isAssignee) {
      projectAccess.requireEdit(activity.getProjectId());
    }

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
    if (request.workActivityId() != null) {
      activity.setWorkActivityId(request.workActivityId());
    }

    // Enforce date-order across the planned window after any updates
    LocalDate ps = activity.getPlannedStartDate();
    LocalDate pf = activity.getPlannedFinishDate();
    if (ps != null && pf != null && pf.isBefore(ps)) {
      throw new BusinessRuleException(
          "INVALID_DATE_RANGE",
          "plannedFinishDate must be on or after plannedStartDate");
    }

    if (progressChanged || statusExplicit) {
      validatePredecessorConstraints(activity);
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

    projectAccess.requireEdit(activity.getProjectId());

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
    projectAccess.requireRead(activity.getProjectId());
    return ActivityResponse.from(activity);
  }

  public PagedResponse<ActivityResponse> listActivities(UUID projectId, Pageable pageable) {
    log.info("Listing activities for project: projectId={}, page={}, size={}", projectId,
        pageable.getPageNumber(), pageable.getPageSize());

    projectAccess.requireRead(projectId);

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
    // Filter to activities the user may read (via the activity's projectId).
    java.util.Set<UUID> allowed = projectAccess.getAccessibleProjectIdsForCurrentUser();
    return activityRepository.findByWbsNodeId(wbsNodeId).stream()
        .filter(a -> allowed == null || allowed.contains(a.getProjectId()))
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
    validatePredecessorConstraints(activity);

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
    ActivityStatus derived;
    if (pct != null && pct >= 100.0) {
      derived = ActivityStatus.COMPLETED;
    } else if ((pct != null && pct > 0.0) || hasActualStart) {
      derived = ActivityStatus.IN_PROGRESS;
    } else {
      derived = ActivityStatus.NOT_STARTED;
    }
    activity.setStatus(derived);
  }

  /**
   * Block out-of-sequence actuals. Each dependency type gates a different transition:
   * <ul>
   *   <li>FS — successor cannot start until predecessor finishes</li>
   *   <li>SS — successor cannot start until predecessor starts</li>
   *   <li>FF — successor cannot finish until predecessor finishes</li>
   *   <li>SF — successor cannot finish until predecessor starts</li>
   * </ul>
   * Lag values aren't enforced here — only the gating-date existence check, since
   * planners often need to log actuals that occurred earlier than the lag would allow.
   * Cross-project (external) relationships are skipped because the predecessor
   * activity isn't queryable from this service.
   */
  private void validatePredecessorConstraints(Activity activity) {
    Double pct = activity.getPercentComplete();
    boolean claimsStarted = activity.getActualStartDate() != null
        || (pct != null && pct > 0.0)
        || activity.getStatus() == ActivityStatus.IN_PROGRESS
        || activity.getStatus() == ActivityStatus.COMPLETED;
    boolean claimsFinished = activity.getActualFinishDate() != null
        || (pct != null && pct >= 100.0)
        || activity.getStatus() == ActivityStatus.COMPLETED;
    if (!claimsStarted && !claimsFinished) {
      return;
    }

    List<ActivityRelationship> predecessors =
        relationshipRepository.findBySuccessorActivityId(activity.getId());
    for (ActivityRelationship rel : predecessors) {
      if (Boolean.TRUE.equals(rel.getIsExternal())) {
        continue;
      }
      Activity pred = activityRepository.findById(rel.getPredecessorActivityId()).orElse(null);
      if (pred == null) {
        continue;
      }
      switch (rel.getRelationshipType()) {
        case FINISH_TO_START -> {
          if (claimsStarted && pred.getActualFinishDate() == null) {
            throw predecessorViolation(activity, pred, "start", "finished", "FS", rel.getLag());
          }
        }
        case START_TO_START -> {
          if (claimsStarted && pred.getActualStartDate() == null) {
            throw predecessorViolation(activity, pred, "start", "started", "SS", rel.getLag());
          }
        }
        case FINISH_TO_FINISH -> {
          if (claimsFinished && pred.getActualFinishDate() == null) {
            throw predecessorViolation(activity, pred, "finish", "finished", "FF", rel.getLag());
          }
        }
        case START_TO_FINISH -> {
          if (claimsFinished && pred.getActualStartDate() == null) {
            throw predecessorViolation(activity, pred, "finish", "started", "SF", rel.getLag());
          }
        }
      }
    }
  }

  private static BusinessRuleException predecessorViolation(
      Activity activity, Activity pred, String successorVerb, String predecessorState,
      String typeCode, Double lag) {
    String lagSuffix = formatLag(lag);
    String message = String.format(
        "Cannot %s %s — predecessor %s (%s) has not %s. Dependency: %s%s.",
        successorVerb, activity.getCode(), pred.getCode(), pred.getName(),
        predecessorState, typeCode, lagSuffix);
    return new BusinessRuleException("PREDECESSOR_NOT_SATISFIED", message);
  }

  private static String formatLag(Double lag) {
    if (lag == null || lag == 0.0) {
      return "";
    }
    long days = Math.round(Math.abs(lag));
    return lag > 0 ? " + " + days + "d" : " - " + days + "d";
  }

  /**
   * Returns the explicit {@code calendarId} if supplied; otherwise falls back to the
   * project's default calendar (P6-style project-calendar inheritance).
   */
  private UUID resolveCalendarId(UUID projectId, UUID explicitCalendarId) {
    if (explicitCalendarId != null) {
      return explicitCalendarId;
    }
    Project project = projectRepository.findById(projectId).orElse(null);
    return project != null ? project.getCalendarId() : null;
  }
}
