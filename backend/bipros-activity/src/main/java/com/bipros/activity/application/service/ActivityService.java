package com.bipros.activity.application.service;

import com.bipros.activity.application.dto.ActivityResponse;
import com.bipros.activity.application.dto.CreateActivityRequest;
import com.bipros.activity.application.dto.SetSupervisorsRequest;
import com.bipros.activity.application.dto.SupervisorEntry;
import com.bipros.activity.application.dto.UpdateActivityRequest;
import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityEditStatus;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.ActivityStatus;
import com.bipros.activity.domain.model.ActivitySupervisor;
import com.bipros.activity.application.percent.ActivityStatusDerivation;
import com.bipros.activity.application.percent.PercentCompleteCalculator;
import com.bipros.activity.domain.model.PercentCompleteType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.activity.domain.repository.ActivityStepRepository;
import com.bipros.activity.domain.repository.ActivitySupervisorRepository;
import com.bipros.common.dto.PagedResponse;
import com.bipros.common.event.ActivityCreatedEvent;
import com.bipros.common.event.ActivityUpdatedEvent;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.security.AccessSpecifications;
import com.bipros.common.security.ProjectAccessGuard;
import com.bipros.common.util.AuditService;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ActivityService {

  private final ActivityRepository activityRepository;
  private final ActivitySupervisorRepository activitySupervisorRepository;
  private final ActivityRelationshipRepository relationshipRepository;
  private final AuditService auditService;
  private final ProjectAccessGuard projectAccess;
  private final ProjectRepository projectRepository;
  private final PercentCompleteCalculator percentCompleteCalculator;
  private final com.bipros.activity.application.percent.ParentRollupCalculator parentRollupCalculator;
  private final ActivityStepRepository stepRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final com.bipros.activity.application.percent.BoqProgressGuard boqProgressGuard;

  /** Cross-schema lookup of {@code resource.work_activities.default_unit} — keeps this module
   *  free of a Maven dep on {@code bipros-resource}, mirroring the precedent in
   *  {@code DailyActivityResourceOutputService}. Used only by list paths that bulk-resolve. */
  @PersistenceContext private EntityManager em;

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

    if (request.parentActivityId() != null) {
      // Hierarchy (D10/D11): validates the containment edge and rewrites the code to
      // parentCode.segment. Safe pre-save — the cycle walk tolerates a null id.
      applyParent(activity, request.parentActivityId());
    }
    // BOQ link (D8/D9): optional at create; validates the line exists in this project and
    // defaults plannedQty to the line's boqQty when this is the only linked activity.
    applyBoqLink(activity, request.boqItemId(), request.boqOperationId(), request.plannedQty(), false);

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
    activity.setCostAccountId(request.costAccountId());
    if (request.preliminary() != null) {
      // DBS-Phase-2 BOQ Section 1 flag. Null on the request → keep the entity default (false);
      // an explicit value lets the admin create an activity already flagged as a preliminary.
      activity.setPreliminary(request.preliminary());
    }
    // Phase 4.5: responsibleResourceId / responsibleResourceName are gone from the DB
    // (Liquibase 094 dropped the columns). Supervisor identity is now carried by
    // supervisor_user_id and set via PUT /v1/activities/{id}/supervisor — the create path
    // no longer wires through a Resource-based supervisor. Intentional no-op.
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

    eventPublisher.publishEvent(
        new ActivityCreatedEvent(saved.getProjectId(), saved.getId(), saved.getCode(), saved.getName())
    );

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
      // Determine effective percentCompleteType: if the request is also changing the type,
      // evaluate against the post-update type so the user can't sneak a manual write past it.
      PercentCompleteType effectiveType = request.percentCompleteType() != null
          ? request.percentCompleteType()
          : activity.getPercentCompleteType();
      if (effectiveType == null) {
        effectiveType = PercentCompleteType.DURATION;
      }
      if (effectiveType != PercentCompleteType.PHYSICAL) {
        throw new BusinessRuleException(
            "PERCENT_COMPLETE_NOT_MANUAL",
            "percentComplete is derived for type=" + effectiveType
                + "; for UNITS edit Daily Outputs, for DURATION edit actual dates / data date.");
      }
      // For PHYSICAL with steps, manual entry is also rejected
      if (effectiveType == PercentCompleteType.PHYSICAL
          && stepRepository.countByActivityId(id) > 0) {
        throw new BusinessRuleException(
            "PERCENT_COMPLETE_OWNED_BY_STEPS",
            "percentComplete is derived from activity steps for this activity. Edit step completion instead.");
      }
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
      // Finishing an activity completes it — 100 ⇔ COMPLETED for every percentCompleteType.
      activity.setPercentComplete(100.0);
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
    // costAccountId: explicit null clears the value; non-null sets it
    if (request.costAccountId() != null) {
      activity.setCostAccountId(request.costAccountId());
    }
    if (request.preliminary() != null) {
      // DBS-Phase-2 BOQ Section 1 flag. Non-null toggles between direct & prelim buckets; null
      // leaves the value untouched (matches the rest of this method's "PATCH-style" semantics).
      activity.setPreliminary(request.preliminary());
    }
    // Phase 4.5: supervisor Resource fields are deprecated (the DB columns are dropped by
    // Liquibase 094). The request still carries them for back-compat with older frontends
    // but the assignment is now made via PUT /v1/activities/{id}/supervisor (supervisor_user_id).
    // Intentional no-op here.

    // Hierarchy (D10/D11): null parentActivityId = unchanged; detaching needs the explicit flag.
    UUID oldParentId = activity.getParentActivityId();
    if (Boolean.TRUE.equals(request.clearParent())) {
      applyParent(activity, null);
    } else if (request.parentActivityId() != null
        && !request.parentActivityId().equals(activity.getParentActivityId())) {
      applyParent(activity, request.parentActivityId());
    }

    // BOQ link (D8/D9): null boqItemId = unchanged; unlinking needs the explicit flag.
    applyBoqLink(activity, request.boqItemId(), request.boqOperationId(), request.plannedQty(),
        Boolean.TRUE.equals(request.clearBoqLink()));

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
    if (!java.util.Objects.equals(oldParentId, updated.getParentActivityId())) {
      auditService.logUpdate("Activity", id, "parentActivityId", oldParentId, updated.getParentActivityId());
      // H5 — the OLD parent chain shrinks by this child; recompute it too.
      recomputeAncestorsFrom(oldParentId);
    }
    // §5.4 — bubble any progress/parent change up the containment chain (no-op when top-level).
    recomputeParentChain(updated.getId());

    eventPublisher.publishEvent(
        new ActivityUpdatedEvent(updated.getProjectId(), updated.getId(), updated.getCode(), updated.getName())
    );

    return ActivityResponse.from(updated);
  }

  public void deleteActivity(UUID id) {
    log.info("Deleting activity: id={}", id);

    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));

    projectAccess.requireEdit(activity.getProjectId());

    // Hierarchy H7: a parent cannot be deleted out from under its children.
    if (activityRepository.existsByParentActivityId(id)) {
      throw new BusinessRuleException("ACTIVITY_HAS_CHILDREN",
          "Cannot delete an activity that has child activities. Re-parent or delete the children first.");
    }

    boolean hasRelationships = !relationshipRepository.findByPredecessorActivityId(id).isEmpty()
        || !relationshipRepository.findBySuccessorActivityId(id).isEmpty();

    if (hasRelationships) {
      throw new BusinessRuleException("ACTIVITY_HAS_RELATIONSHIPS",
          "Cannot delete activity with relationships. Remove relationships first.");
    }

    // Clear any supervisor rows first — Activity has no JPA cascade to ActivitySupervisor
    // (kept as a sibling aggregate to avoid pulling the supervisor list on every Activity load).
    activitySupervisorRepository.deleteByActivityId(id);
    activityRepository.deleteById(id);
    log.info("Activity deleted successfully: id={}", id);

    // Audit log deletion
    auditService.logDelete("Activity", id);
  }

  public ActivityResponse getActivity(UUID id) {
    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
    projectAccess.requireRead(activity.getProjectId());
    // Read the STORED percentComplete/status — identical to the list path, so the detail page
    // and the list can never disagree. Writers (BOQ listener, duration/units listeners, nightly
    // job, Complete button) keep the stored value current.
    List<SupervisorEntry> supervisors = loadSupervisorsByActivityId(List.of(id))
        .getOrDefault(id, List.of());
    return ActivityResponse.from(activity, (String) null, supervisors);
  }

  public PagedResponse<ActivityResponse> listActivities(UUID projectId, Pageable pageable) {
    log.info("Listing activities for project: projectId={}, page={}, size={}", projectId,
        pageable.getPageNumber(), pageable.getPageSize());

    projectAccess.requireRead(projectId);

    Page<Activity> page = activityRepository.findByProjectIdOrderBySortOrder(projectId, pageable);
    Map<UUID, String> defaultUnitsByWorkActivity =
        bulkResolveWorkActivityDefaultUnits(page.getContent());
    Map<UUID, List<SupervisorEntry>> supervisorsByActivityId =
        loadSupervisorsByActivityId(page.getContent().stream().map(Activity::getId).toList());
    return PagedResponse.of(
        page.getContent().stream()
            .map(a -> ActivityResponse.from(a,
                a.getWorkActivityId() == null ? null : defaultUnitsByWorkActivity.get(a.getWorkActivityId()),
                supervisorsByActivityId.getOrDefault(a.getId(), List.of())))
            .toList(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize()
    );
  }

  /**
   * Bulk fetch supervisor entries for the given activity ids. Preserves insertion order
   * (createdAt ASC) within each activity so the legacy first-supervisor cache stays stable.
   * Returns an empty map (not null) when the input is empty.
   */
  private Map<UUID, List<SupervisorEntry>> loadSupervisorsByActivityId(List<UUID> activityIds) {
    if (activityIds == null || activityIds.isEmpty()) return Map.of();
    // Copy into a mutable list — Spring Data may return an immutable view, and tests mock
    // with List.of(). Sort by createdAt so "first item" semantics are stable across reads.
    List<ActivitySupervisor> rows = new ArrayList<>(
        activitySupervisorRepository.findByActivityIdIn(activityIds));
    rows.sort((a, b) -> {
      if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
      if (a.getCreatedAt() == null) return -1;
      if (b.getCreatedAt() == null) return 1;
      return a.getCreatedAt().compareTo(b.getCreatedAt());
    });

    // Resolve display names for any row missing a snapshot (pure-API callers, legacy data).
    Set<UUID> needName = new java.util.HashSet<>();
    for (ActivitySupervisor s : rows) {
      if (s.getUserNameSnapshot() == null || s.getUserNameSnapshot().isBlank()) {
        if (s.getUserId() != null) needName.add(s.getUserId());
      }
    }
    Map<UUID, String> resolved = needName.isEmpty() ? Map.of() : bulkResolveUserDisplayNames(needName);

    Map<UUID, List<SupervisorEntry>> out = new HashMap<>();
    for (ActivitySupervisor s : rows) {
      String name = s.getUserNameSnapshot();
      if ((name == null || name.isBlank()) && s.getUserId() != null) {
        name = resolved.get(s.getUserId());
      }
      out.computeIfAbsent(s.getActivityId(), k -> new ArrayList<>())
          .add(new SupervisorEntry(s.getUserId(), name));
    }
    return out;
  }

  /**
   * Best-effort lookup of "{first_name} {last_name}" (falling back to {@code username}) from
   * {@code public.users} for the given user ids. Used to backfill display names when the
   * snapshot column on {@code activity_supervisors} wasn't populated at write time.
   */
  @SuppressWarnings("unchecked")
  private Map<UUID, String> bulkResolveUserDisplayNames(Set<UUID> userIds) {
    if (em == null || userIds == null || userIds.isEmpty()) return Map.of();
    java.util.List<Object[]> rows = em.createNativeQuery(
            "SELECT id, first_name, last_name, username FROM public.users WHERE id IN (:ids)")
        .setParameter("ids", userIds)
        .getResultList();
    Map<UUID, String> out = new HashMap<>(rows.size());
    for (Object[] r : rows) {
      UUID id = (UUID) r[0];
      String fn = r[1] == null ? null : r[1].toString().trim();
      String ln = r[2] == null ? null : r[2].toString().trim();
      String un = r[3] == null ? null : r[3].toString().trim();
      String full;
      if ((fn != null && !fn.isEmpty()) || (ln != null && !ln.isEmpty())) {
        full = ((fn == null ? "" : fn) + " " + (ln == null ? "" : ln)).trim();
      } else {
        full = un;
      }
      if (full != null && !full.isEmpty()) out.put(id, full);
    }
    return out;
  }

  /**
   * One-shot lookup of {@code work_activities.default_unit} for every distinct work_activity_id
   * referenced by the page. Avoids N+1 queries when the page has many activities.
   */
  @SuppressWarnings("unchecked")
  private Map<UUID, String> bulkResolveWorkActivityDefaultUnits(List<Activity> activities) {
    if (em == null) return Map.of();
    Set<UUID> ids = activities.stream()
        .map(Activity::getWorkActivityId)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    if (ids.isEmpty()) return Map.of();
    List<Object[]> rows = em.createNativeQuery(
            "SELECT id, default_unit FROM resource.work_activities WHERE id IN (:ids)")
        .setParameter("ids", ids)
        .getResultList();
    Map<UUID, String> out = new HashMap<>(rows.size());
    for (Object[] r : rows) {
      UUID id = (UUID) r[0];
      String unit = r[1] == null ? null : r[1].toString();
      out.put(id, unit);
    }
    return out;
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

    // Hierarchy D10: a parent's % is rolled up from its children — never edited directly.
    if (activityRepository.existsByParentActivityId(id)) {
      throw new BusinessRuleException("ACTIVITY_IS_PARENT",
          "This activity's progress rolls up from its children — update the children instead.");
    }

    if (percentComplete < 0 || percentComplete > 100) {
      throw new BusinessRuleException("INVALID_PERCENT_COMPLETE",
          "Percent complete must be between 0 and 100");
    }

    // Guard: reject manual % edits for non-PHYSICAL types
    PercentCompleteType pctType = activity.getPercentCompleteType();
    if (pctType == null) {
      pctType = PercentCompleteType.DURATION;
    }
    if (pctType != PercentCompleteType.PHYSICAL) {
      throw new BusinessRuleException(
          "PERCENT_COMPLETE_NOT_MANUAL",
          "percentComplete is derived for type=" + pctType
              + "; for UNITS edit Daily Outputs, for DURATION edit actual dates / data date.");
    }
    if (stepRepository.countByActivityId(id) > 0) {
      throw new BusinessRuleException(
          "PERCENT_COMPLETE_OWNED_BY_STEPS",
          "percentComplete is derived from activity steps for this activity. Edit step completion instead.");
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

    // §5.4 — bubble the change up the containment chain (no-op when top-level).
    recomputeParentChain(updated.getId());

    return ActivityResponse.from(updated);
  }

  /**
   * @deprecated Phase 4.5: this endpoint synced the legacy
   * {@code Activity.responsibleResourceId} cache, which is dropped by Liquibase 094. The
   * canonical supervisor wiring is now the per-activity
   * {@code PUT /v1/activities/{id}/supervisor} endpoint that writes
   * {@code Activity.supervisorUserId}. The method is preserved (no signature change) and
   * short-circuits to {@code 0} so older frontends do not 500. New callers must use the
   * user-based supervisor endpoint.
   */
  /**
   * Legacy single-supervisor write — kept for back-compat with older frontends and any
   * external integrations. Delegates to {@link #setSupervisors} with a one-element list,
   * which means it now REPLACES the entire supervisor set on the activity. Pass
   * {@code supervisorUserId = null} to clear.
   */
  public ActivityResponse setSupervisor(UUID activityId,
      com.bipros.activity.application.dto.SetSupervisorRequest request) {
    UUID userId = request == null ? null : request.supervisorUserId();
    String snapshot = request == null ? null : request.supervisorName();
    List<SupervisorEntry> list = userId == null
        ? List.of()
        : List.of(new SupervisorEntry(userId, snapshot));
    return setSupervisors(activityId, new SetSupervisorsRequest(list));
  }

  /**
   * Replace the supervisor set on an activity. All entries are equal — no primary.
   * Duplicate user ids in the request are deduplicated (first wins for the name
   * snapshot). The legacy single-column cache on {@code activities} is kept in sync
   * with the first entry for back-compat with consumers still reading
   * {@code supervisor_user_id} / {@code supervisor_user_name} directly.
   */
  public ActivityResponse setSupervisors(UUID activityId, SetSupervisorsRequest request) {
    Activity activity = activityRepository.findById(activityId)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));
    projectAccess.requireEdit(activity.getProjectId());

    List<SupervisorEntry> requested = request == null || request.supervisors() == null
        ? List.of()
        : request.supervisors();
    // Dedup preserving first occurrence; drop null userIds defensively.
    LinkedHashMap<UUID, String> deduped = new LinkedHashMap<>();
    for (SupervisorEntry e : requested) {
      if (e == null || e.userId() == null) continue;
      deduped.putIfAbsent(e.userId(), e.userName());
    }

    // Diff existing vs target and apply minimal mutations so createdAt timestamps survive
    // and the first-entry cache only flips when the first item actually changes.
    List<ActivitySupervisor> existing = activitySupervisorRepository.findByActivityId(activityId);
    Map<UUID, ActivitySupervisor> existingByUser = new HashMap<>();
    for (ActivitySupervisor s : existing) existingByUser.put(s.getUserId(), s);

    for (Map.Entry<UUID, String> entry : deduped.entrySet()) {
      ActivitySupervisor row = existingByUser.remove(entry.getKey());
      if (row == null) {
        ActivitySupervisor fresh = new ActivitySupervisor();
        fresh.setActivityId(activityId);
        fresh.setUserId(entry.getKey());
        fresh.setUserNameSnapshot(entry.getValue());
        activitySupervisorRepository.save(fresh);
      } else if (!java.util.Objects.equals(row.getUserNameSnapshot(), entry.getValue())) {
        row.setUserNameSnapshot(entry.getValue());
        activitySupervisorRepository.save(row);
      }
    }
    // Anything left in existingByUser was not in the request → remove.
    for (ActivitySupervisor stale : existingByUser.values()) {
      activitySupervisorRepository.delete(stale);
    }

    // Sync the legacy single-column cache. First entry wins; null when empty.
    if (deduped.isEmpty()) {
      activity.setSupervisorUserId(null);
      activity.setSupervisorUserName(null);
    } else {
      Map.Entry<UUID, String> first = deduped.entrySet().iterator().next();
      activity.setSupervisorUserId(first.getKey());
      activity.setSupervisorUserName(first.getValue());
    }
    Activity saved = activityRepository.save(activity);
    log.info("Set supervisors: activityId={}, count={}", activityId, deduped.size());

    List<SupervisorEntry> attached = loadSupervisorsByActivityId(List.of(activityId))
        .getOrDefault(activityId, List.of());
    return ActivityResponse.from(saved, (String) null, attached);
  }

  @Deprecated(forRemoval = true)
  public int bulkSetSupervisor(UUID projectId, com.bipros.activity.application.dto.BulkSupervisorRequest request) {
    log.warn("Phase 4.5: bulkSetSupervisor is a no-op (responsibleResourceId column dropped). "
        + "Use the supervisor_user_id endpoint instead. projectId={}, requested={}",
        projectId, request != null && request.activityIds() != null ? request.activityIds().size() : 0);
    projectAccess.requireEdit(projectId);
    return 0;
  }

  public void applyActuals(UUID projectId, LocalDate dataDate) {
    log.info("Applying actuals for project: projectId={}, dataDate={}", projectId, dataDate);

    java.util.List<Activity> activities = activityRepository.findByProjectId(projectId);

    for (Activity activity : activities) {
      // Skip LOCKED activities — their plan and actuals are frozen. DPR-driven
      // cascade writes are the only mutator that bypasses this; the batch apply-actuals
      // is treated like a manual cascade and respects the lock.
      if (activity.getEditStatus() == ActivityEditStatus.LOCKED) {
        continue;
      }
      boolean updated = false;

      // Auto-stamp actuals from the data date — this is what `applyActuals` adds
      // on top of the calculator. The calculator itself does not write actual dates.
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
        updated = true;
      }

      // Percent / status / forced-finish for non-BOQ activities only. BOQ-driven activities
      // have their percentComplete owned by ActivityProgressFromBoqListener (precedence #1),
      // so apply-actuals must not clobber it with the time-elapsed value. The date auto-stamp
      // above still applies. UNITS rollups are event-driven elsewhere — pass null sums and let
      // calculateUnits return KEEP_PRIOR for those.
      if (!boqProgressGuard.isBoqDriven(activity.getId())) {
        var result = percentCompleteCalculator.calculate(activity, null, null, dataDate);
        if (!result.isKeepPrior()) {
          if (result.percent() != null
              && !java.util.Objects.equals(result.percent(), activity.getPercentComplete())) {
            activity.setPercentComplete(result.percent());
            updated = true;
          }
          if (result.status() != null && result.status() != activity.getStatus()) {
            activity.setStatus(result.status());
            updated = true;
          }
          if (result.forcedActualFinish() != null && activity.getActualFinishDate() == null) {
            activity.setActualFinishDate(result.forcedActualFinish());
            updated = true;
          }
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
   * Flip the activity to LOCKED. Idempotent — locking an already-locked activity
   * is a no-op success. Requires {@code ACTIVITY.LOCK} on the project (enforced
   * at the controller boundary).
   */
  public ActivityResponse lockActivity(UUID id) {
    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
    // Hierarchy H10: parents have no resource plan or DPRs, so lock stays a leaf concept.
    if (activityRepository.existsByParentActivityId(id)) {
      throw new BusinessRuleException("ACTIVITY_IS_PARENT",
          "Parents are grouping nodes — lock the child activities instead.");
    }
    if (activity.getEditStatus() == ActivityEditStatus.LOCKED) {
      return ActivityResponse.from(activity);
    }
    ActivityEditStatus prior = activity.getEditStatus();
    activity.setEditStatus(ActivityEditStatus.LOCKED);
    Activity saved = activityRepository.save(activity);
    log.info("Activity locked: id={}", id);
    auditService.logUpdate("Activity", id, "editStatus", prior, ActivityEditStatus.LOCKED);
    return ActivityResponse.from(saved);
  }

  /**
   * Flip the activity back to DRAFT. Idempotent. Requires {@code ACTIVITY.UNLOCK}
   * on the project (enforced at the controller boundary). DPRs already submitted
   * while locked are unaffected; they continue to exist and their cascade history
   * is preserved.
   */
  public ActivityResponse unlockActivity(UUID id) {
    Activity activity = activityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
    if (activity.getEditStatus() == ActivityEditStatus.DRAFT) {
      return ActivityResponse.from(activity);
    }
    ActivityEditStatus prior = activity.getEditStatus();
    activity.setEditStatus(ActivityEditStatus.DRAFT);
    Activity saved = activityRepository.save(activity);
    log.info("Activity unlocked: id={}", id);
    auditService.logUpdate("Activity", id, "editStatus", prior, ActivityEditStatus.DRAFT);
    return ActivityResponse.from(saved);
  }

  // ─── Hierarchy (design D10/D11) ─────────────────────────────────────────────────

  /** Spec H1/H4/C1-C3: validate the containment edge and regenerate dotted codes. */
  private void applyParent(Activity child, UUID newParentId) {
    if (newParentId == null) {
      child.setParentActivityId(null);
      regenerateCode(child, null);   // detach → code collapses back to the bare segment
      return;
    }
    if (newParentId.equals(child.getId())) {
      throw new BusinessRuleException("ACTIVITY_PARENT_CYCLE", "An activity cannot be its own parent.");
    }
    Activity parent = activityRepository.findById(newParentId)
        .orElseThrow(() -> new ResourceNotFoundException("Activity", newParentId));
    if (!parent.getProjectId().equals(child.getProjectId())) {
      throw new BusinessRuleException("ACTIVITY_PARENT_PROJECT", "Parent must belong to the same project.");
    }
    // H1 — walk up from the proposed parent; meeting the child means a cycle.
    UUID cursor = parent.getParentActivityId();
    int hops = 0;
    while (cursor != null && hops++ < 200) {
      if (cursor.equals(child.getId())) {
        throw new BusinessRuleException("ACTIVITY_PARENT_CYCLE", "This move would create a cycle.");
      }
      cursor = activityRepository.findById(cursor).map(Activity::getParentActivityId).orElse(null);
    }
    // H4 — a node gaining its FIRST child must hold no scheduling relationships
    // (parents are excluded from CPM; silently orphaning edges would corrupt the schedule).
    if (!activityRepository.existsByParentActivityId(parent.getId())
        && (!relationshipRepository.findByPredecessorActivityId(parent.getId()).isEmpty()
            || !relationshipRepository.findBySuccessorActivityId(parent.getId()).isEmpty())) {
      throw new BusinessRuleException("ACTIVITY_PARENT_HAS_RELATIONSHIPS",
          "Remove or move this activity's schedule links before adding children — parents hold no dependencies.");
    }
    // H11 (reverse) — a node gaining its FIRST child must not carry a BOQ link either:
    // links live on leaves, or the line would be double-represented in progress.
    if (!activityRepository.existsByParentActivityId(parent.getId()) && parent.getBoqItemId() != null) {
      throw new BusinessRuleException("ACTIVITY_PARENT_HAS_BOQ_LINK",
          "Remove this activity's BOQ link before adding children — links live on the child activities.");
    }
    child.setParentActivityId(parent.getId());
    if (child.getWbsNodeId() == null) {
      child.setWbsNodeId(parent.getWbsNodeId());   // H8 default (create paths always set WBS, so this is a safety net)
    }
    regenerateCode(child, parent);
  }

  /** C1-C3: full code = parent's full code + "." + own segment; descendants follow recursively. */
  private void regenerateCode(Activity node, Activity parent) {
    String segment = segmentOf(node.getCode());
    String full = parent == null ? segment : parent.getCode() + "." + segment;
    if (full.length() > 120) {
      throw new BusinessRuleException("ACTIVITY_CODE_TOO_LONG",
          "Nested code '" + full + "' exceeds 120 characters — shorten the activity's code segment.");
    }
    if (!full.equals(node.getCode())
        && activityRepository.existsByProjectIdAndCode(node.getProjectId(), full)) {
      throw new BusinessRuleException("ACTIVITY_CODE_DUPLICATE",
          "Code " + full + " already exists in this project.");
    }
    node.setCode(full);
    if (node.getId() == null) {
      return;   // brand-new activity — no descendants yet
    }
    for (Activity c : activityRepository.findByProjectIdAndParentActivityId(node.getProjectId(), node.getId())) {
      regenerateCode(c, node);   // C2 — the moved subtree's codes follow
      activityRepository.save(c);
    }
  }

  /** The activity's own segment = the part after the last dot (the whole code when top-level). */
  private static String segmentOf(String code) {
    int i = code.lastIndexOf('.');
    return i < 0 ? code : code.substring(i + 1);
  }

  // ─── BOQ link (design D8/D9/§5.3) ───────────────────────────────────────────────

  /**
   * Set / change / clear the activity's BOQ link. Hard rules only — the WBS-divergence
   * warning (D13) is rendered client-side where the picker already has both WBS ids.
   * When this is the only activity linked to the line and no explicit {@code plannedQty}
   * was given, it defaults to the line's {@code boqQty} (§5.3 — zero extra data entry,
   * and guarantees the sole-linked activity's % basis equals today's).
   */
  private void applyBoqLink(Activity a, UUID boqItemId, UUID boqOperationId,
                            java.math.BigDecimal plannedQty, boolean clearLink) {
    if (clearLink) {
      a.setBoqItemId(null);
      a.setBoqOperationId(null);
      a.setPlannedQty(null);
      return;
    }
    if (boqItemId != null && !boqItemId.equals(a.getBoqItemId())) {
      // H11 — parents carry no links; the children do the work.
      if (a.getId() != null && activityRepository.existsByParentActivityId(a.getId())) {
        throw new BusinessRuleException("ACTIVITY_PARENT_NO_LINK",
            "Parents don't carry BOQ links — link the child activity that does the work.");
      }
      List<?> rows = em.createNativeQuery(
              "SELECT b.boq_qty FROM project.boq_items b "
                  + "WHERE b.id = cast(:id as uuid) AND b.project_id = cast(:pid as uuid)")
          .setParameter("id", boqItemId.toString())
          .setParameter("pid", a.getProjectId().toString())
          .getResultList();
      if (rows.isEmpty()) {
        throw new ResourceNotFoundException("BoqItem", boqItemId);
      }
      a.setBoqItemId(boqItemId);
      a.setBoqOperationId(null);   // a re-point resets any stale operation of the old line
      Object boqQty = rows.get(0);
      // Line-based plannedQty default only when no operation is being assigned — the
      // operation's own target wins below (Stage 4).
      if (boqOperationId == null && plannedQty == null && a.getPlannedQty() == null
          && boqQty != null && countOtherActivitiesLinkedTo(boqItemId, a.getId()) == 0) {
        a.setPlannedQty(new java.math.BigDecimal(boqQty.toString()));
      }
    }
    // Stage 4: point the activity at one operation of its (split) line. Validated against the
    // CURRENT line so a stale operation id from another line can never be attached.
    if (boqOperationId != null && !boqOperationId.equals(a.getBoqOperationId())) {
      if (a.getBoqItemId() == null) {
        throw new BusinessRuleException("ACTIVITY_BOQ_OPERATION_MISMATCH",
            "Link the activity to a BOQ line before picking one of its operations.");
      }
      List<Object[]> opRows = em.createNativeQuery(
              "SELECT o.target_qty, o.is_legacy FROM project.boq_operations o "
                  + "WHERE o.id = cast(:op as uuid) AND o.boq_item_id = cast(:b as uuid)")
          .setParameter("op", boqOperationId.toString())
          .setParameter("b", a.getBoqItemId().toString())
          .getResultList();
      if (opRows.isEmpty()) {
        throw new BusinessRuleException("ACTIVITY_BOQ_OPERATION_MISMATCH",
            "That operation does not belong to the activity's linked BOQ line.");
      }
      Object[] op = opRows.get(0);
      if (Boolean.TRUE.equals(op[1])) {
        throw new BusinessRuleException("ACTIVITY_BOQ_OPERATION_MISMATCH",
            "That is the line's pre-split history operation — pick a real operation.");
      }
      a.setBoqOperationId(boqOperationId);
      // §5.3 carried to operations: sole-covering activity's plannedQty defaults to the
      // operation's target.
      if (plannedQty == null && a.getPlannedQty() == null && op[0] != null
          && countOtherActivitiesOnOperation(boqOperationId, a.getId()) == 0) {
        a.setPlannedQty(new java.math.BigDecimal(op[0].toString()));
      }
    }
    if (plannedQty != null) {
      if (plannedQty.signum() <= 0) {
        throw new BusinessRuleException("ACTIVITY_PLANNED_QTY_INVALID",
            "Planned quantity must be greater than zero.");
      }
      a.setPlannedQty(plannedQty);
    }
  }

  private long countOtherActivitiesOnOperation(UUID boqOperationId, UUID excludeActivityId) {
    String exclude = excludeActivityId == null ? new UUID(0, 0).toString() : excludeActivityId.toString();
    return ((Number) em.createNativeQuery(
            "SELECT count(*) FROM activity.activities "
                + "WHERE boq_operation_id = cast(:o as uuid) AND id <> cast(:a as uuid)")
        .setParameter("o", boqOperationId.toString())
        .setParameter("a", exclude)
        .getSingleResult()).longValue();
  }

  private long countOtherActivitiesLinkedTo(UUID boqItemId, UUID excludeActivityId) {
    String exclude = excludeActivityId == null ? new UUID(0, 0).toString() : excludeActivityId.toString();
    return ((Number) em.createNativeQuery(
            "SELECT count(*) FROM activity.activities "
                + "WHERE boq_item_id = cast(:b as uuid) AND id <> cast(:a as uuid)")
        .setParameter("b", boqItemId.toString())
        .setParameter("a", exclude)
        .getSingleResult()).longValue();
  }

  /**
   * §5.4 rollup: after a child's % changes, recompute every ancestor's rolled-up
   * percentComplete + derived status. Public so the BOQ progress listener (same module)
   * can bubble DPR-driven changes up the tree. No-op for top-level activities.
   */
  public void recomputeParentChain(UUID childActivityId) {
    if (childActivityId == null) {
      return;
    }
    UUID parentId = activityRepository.findById(childActivityId)
        .map(Activity::getParentActivityId).orElse(null);
    recomputeAncestorsFrom(parentId);
  }

  /** Walk upward from {@code parentId}, recomputing each ancestor. Cycle-bounded like applyParent. */
  private void recomputeAncestorsFrom(UUID parentId) {
    int hops = 0;
    while (parentId != null && hops++ < 200) {
      Activity parent = activityRepository.findById(parentId).orElse(null);
      if (parent == null) {
        return;
      }
      List<Activity> children =
          activityRepository.findByProjectIdAndParentActivityId(parent.getProjectId(), parent.getId());
      Map<UUID, java.math.BigDecimal> costs = plannedCostByChildrenOf(parent.getId());
      List<com.bipros.activity.application.percent.ParentRollupCalculator.ChildSnapshot> snaps =
          children.stream()
              .map(c -> new com.bipros.activity.application.percent.ParentRollupCalculator.ChildSnapshot(
                  c.getPercentComplete() == null ? 0.0 : c.getPercentComplete(),
                  costs.getOrDefault(c.getId(), java.math.BigDecimal.ZERO),
                  c.getStatus()))
              .toList();
      var result = parentRollupCalculator.rollup(snaps);
      parent.setPercentComplete(result.percentComplete());
      parent.setStatus(result.derivedStatus());
      activityRepository.save(parent);
      parentId = parent.getParentActivityId();
    }
  }

  /**
   * Planned resource-plan cost per child of {@code parentId} — the rollup weights (§5.4).
   * Cross-schema native SQL (same precedent as the default-unit lookup above): role/legacy
   * assignments plus sub-contractor assignments, both of which store {@code planned_cost}.
   */
  @SuppressWarnings("unchecked")
  private Map<UUID, java.math.BigDecimal> plannedCostByChildrenOf(UUID parentId) {
    List<Object[]> rows = em.createNativeQuery(
            "SELECT t.activity_id, SUM(t.pc) FROM ("
                + "  SELECT ra.activity_id, COALESCE(ra.planned_cost, 0) AS pc"
                + "    FROM resource.resource_assignments ra"
                + "    JOIN activity.activities a ON a.id = ra.activity_id"
                + "   WHERE a.parent_activity_id = cast(:pid as uuid)"
                + "  UNION ALL"
                + "  SELECT sc.activity_id, COALESCE(sc.planned_cost, 0)"
                + "    FROM resource.activity_sub_contractor_assignments sc"
                + "    JOIN activity.activities a ON a.id = sc.activity_id"
                + "   WHERE a.parent_activity_id = cast(:pid as uuid)"
                + ") t GROUP BY t.activity_id")
        .setParameter("pid", parentId.toString())
        .getResultList();
    Map<UUID, java.math.BigDecimal> out = new HashMap<>();
    for (Object[] row : rows) {
      UUID id = row[0] instanceof UUID u ? u : UUID.fromString(row[0].toString());
      out.put(id, new java.math.BigDecimal(row[1].toString()));
    }
    return out;
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
    ActivityStatus derived = ActivityStatusDerivation.derive(activity);
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

  /**
   * List activities under {@code projectId} that have no {@code work_activity_id} linked AND
   * either have at least one DPR submitted in the [from, to] window or have planned dates that
   * intersect the window. Powers the "N activities have no Work Activity linked" banner on the
   * Capacity Utilization page.
   *
   * <p>The query crosses the project schema (for the DPR existence check) so a native SQL is
   * used; the same precedent applies as elsewhere in this service.
   */
  @Transactional(readOnly = true)
  public List<com.bipros.activity.application.dto.MissingWorkActivityRow> listMissingWorkActivity(
      UUID projectId, LocalDate from, LocalDate to) {
    projectAccess.requireRead(projectId);
    LocalDate fromDate = from != null ? from : LocalDate.now().minusYears(5);
    LocalDate toDate = to != null ? to : LocalDate.now().plusYears(5);

    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery(
            "SELECT a.id, a.code, a.name, "
                + "  (SELECT COUNT(*) FROM project.daily_progress_reports d "
                + "     WHERE d.activity_id = a.id AND d.report_date BETWEEN :fromDate AND :toDate) "
                + "FROM activity.activities a "
                + "WHERE a.project_id = :projectId "
                + "  AND a.work_activity_id IS NULL "
                + "  AND ( "
                + "    EXISTS (SELECT 1 FROM project.daily_progress_reports d "
                + "             WHERE d.activity_id = a.id AND d.report_date BETWEEN :fromDate AND :toDate) "
                + "    OR (a.planned_start_date <= :toDate AND a.planned_finish_date >= :fromDate) "
                + "  ) "
                + "ORDER BY a.code")
        .setParameter("projectId", projectId)
        .setParameter("fromDate", fromDate)
        .setParameter("toDate", toDate)
        .getResultList();

    return rows.stream()
        .map(r -> new com.bipros.activity.application.dto.MissingWorkActivityRow(
            (UUID) r[0],
            (String) r[1],
            (String) r[2],
            r[3] == null ? 0 : ((Number) r[3]).intValue()))
        .toList();
  }
}
