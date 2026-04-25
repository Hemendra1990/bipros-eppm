package com.bipros.baseline.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.baseline.application.dto.BaselineActivityResponse;
import com.bipros.baseline.application.dto.BaselineDetailResponse;
import com.bipros.baseline.application.dto.BaselineResponse;
import com.bipros.baseline.application.dto.BaselineVarianceResponse;
import com.bipros.baseline.application.dto.CreateBaselineRequest;
import com.bipros.baseline.application.dto.ScheduleComparisonResponse;
import com.bipros.baseline.domain.Baseline;
import com.bipros.baseline.domain.BaselineActivity;
import com.bipros.baseline.infrastructure.repository.BaselineActivityRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRelationshipRepository;
import com.bipros.baseline.infrastructure.repository.BaselineRepository;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.cost.domain.entity.ActivityExpense;
import com.bipros.cost.domain.repository.ActivityExpenseRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BaselineService {

  private final BaselineRepository baselineRepository;
  private final BaselineActivityRepository baselineActivityRepository;
  private final BaselineRelationshipRepository baselineRelationshipRepository;
  private final ActivityRepository activityRepository;
  private final ActivityExpenseRepository activityExpenseRepository;
  private final ResourceAssignmentRepository resourceAssignmentRepository;
  private final ProjectRepository projectRepository;
  private final AuditService auditService;

  @Transactional
  public BaselineResponse createBaseline(UUID projectId, CreateBaselineRequest request) {
    // Enforce name-uniqueness per project+type so the schedule history can't collect
    // indistinguishable "BL-QA-001" snapshots (BUG-037).
    List<Baseline> existingBaselines =
        baselineRepository.findByProjectIdAndBaselineType(projectId, request.baselineType());
    for (Baseline baseline : existingBaselines) {
      if (request.name() != null && request.name().equalsIgnoreCase(baseline.getName())) {
        throw new com.bipros.common.exception.BusinessRuleException(
            "DUPLICATE_CODE",
            "A baseline named '" + request.name() + "' already exists for this project and type");
      }
      baseline.setIsActive(false);
      baselineRepository.save(baseline);
    }

    // Load all activities for this project
    List<Activity> activities = activityRepository.findByProjectId(projectId);

    // Load cost data
    List<ActivityExpense> allExpenses = activityExpenseRepository.findByProjectId(projectId);
    Map<UUID, List<ActivityExpense>> expensesByActivity = allExpenses.stream()
        .filter(e -> e.getActivityId() != null)
        .collect(Collectors.groupingBy(ActivityExpense::getActivityId));

    List<ResourceAssignment> allAssignments = resourceAssignmentRepository.findByProjectId(projectId);
    Map<UUID, List<ResourceAssignment>> assignmentsByActivity = allAssignments.stream()
        .collect(Collectors.groupingBy(ResourceAssignment::getActivityId));

    // Compute project-level metrics BEFORE the first save so totalActivities/totalCost/
    // project dates land in the initial INSERT — otherwise the response DTO may be serialised
    // from a stale entity reference and render as 0/null (BUG-038).
    BigDecimal totalCost = BigDecimal.ZERO;
    LocalDate projectStart = null;
    LocalDate projectFinish = null;
    java.util.List<BaselineActivity> stagedActivities = new java.util.ArrayList<>(activities.size());

    for (Activity activity : activities) {
      BigDecimal plannedCost = calculatePlannedCost(activity, expensesByActivity, assignmentsByActivity);
      BigDecimal actualCost = calculateActualCost(activity, expensesByActivity, assignmentsByActivity);

      BaselineActivity ba = new BaselineActivity();
      ba.setActivityId(activity.getId());
      ba.setEarlyStart(activity.getPlannedStartDate());
      ba.setEarlyFinish(activity.getPlannedFinishDate());
      ba.setLateStart(activity.getLateStartDate());
      ba.setLateFinish(activity.getLateFinishDate());
      ba.setOriginalDuration(activity.getOriginalDuration());
      ba.setRemainingDuration(activity.getRemainingDuration());
      ba.setTotalFloat(activity.getTotalFloat());
      ba.setFreeFloat(activity.getFreeFloat());
      ba.setPlannedCost(plannedCost);
      ba.setActualCost(actualCost);
      ba.setPercentComplete(activity.getPercentComplete());
      stagedActivities.add(ba);

      totalCost = totalCost.add(plannedCost);

      if (activity.getPlannedStartDate() != null) {
        if (projectStart == null || activity.getPlannedStartDate().isBefore(projectStart)) {
          projectStart = activity.getPlannedStartDate();
        }
      }
      if (activity.getPlannedFinishDate() != null) {
        if (projectFinish == null || activity.getPlannedFinishDate().isAfter(projectFinish)) {
          projectFinish = activity.getPlannedFinishDate();
        }
      }
    }

    Baseline baseline = new Baseline();
    baseline.setProjectId(projectId);
    baseline.setName(request.name());
    baseline.setDescription(request.description());
    baseline.setBaselineType(request.baselineType());
    baseline.setBaselineDate(LocalDate.now());
    baseline.setIsActive(true);
    baseline.setTotalActivities(activities.size());
    baseline.setTotalCost(totalCost);
    baseline.setProjectStartDate(projectStart);
    baseline.setProjectFinishDate(projectFinish);
    baseline.setProjectDuration(projectStart != null && projectFinish != null
        ? (double) ChronoUnit.DAYS.between(projectStart, projectFinish)
        : 0.0);
    Baseline saved = baselineRepository.save(baseline);

    for (BaselineActivity ba : stagedActivities) {
      ba.setBaselineId(saved.getId());
      baselineActivityRepository.save(ba);
    }

    // Auto-activate the first baseline a project gets — variance reports are useless
    // until SOMETHING is the reference, and forcing the user to click "Activate" right
    // after "Create" is friction. Subsequent baselines do not auto-replace the active
    // one; the user has to explicitly switch via setActiveBaseline().
    projectRepository.findById(projectId).ifPresent(p -> {
      if (p.getActiveBaselineId() == null) {
        p.setActiveBaselineId(saved.getId());
        projectRepository.save(p);
      }
    });

    auditService.logCreate("Baseline", saved.getId(), BaselineResponse.from(saved));
    return BaselineResponse.from(saved);
  }

  /**
   * Set the given baseline as the project's active reference. P6 calls this the
   * "Project Baseline". Idempotent — calling it with the already-active baseline is fine.
   * Returns the updated baseline.
   */
  @Transactional
  public BaselineResponse setActiveBaseline(UUID projectId, UUID baselineId) {
    Baseline baseline = baselineRepository.findById(baselineId)
        .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineId));
    if (!baseline.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Baseline", baselineId);
    }
    Project project = projectRepository.findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    UUID previousBaselineId = project.getActiveBaselineId();
    project.setActiveBaselineId(baselineId);
    projectRepository.save(project);
    auditService.logUpdate("Project", projectId, "activeBaselineId", previousBaselineId, baselineId);
    return BaselineResponse.from(baseline);
  }

  public BaselineDetailResponse getBaseline(UUID baselineId) {
    Baseline baseline =
        baselineRepository
            .findById(baselineId)
            .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineId));

    List<BaselineActivity> activities = baselineActivityRepository.findByBaselineId(baselineId);
    List<BaselineActivityResponse> activityResponses =
        activities.stream().map(BaselineActivityResponse::from).toList();

    return new BaselineDetailResponse(
        BaselineResponse.from(baseline), activityResponses);
  }

  public List<BaselineResponse> listBaselines(UUID projectId) {
    return baselineRepository.findByProjectId(projectId).stream()
        .map(BaselineResponse::from)
        .toList();
  }

  @Transactional
  public void deleteBaseline(UUID baselineId) {
    Baseline baseline =
        baselineRepository
            .findById(baselineId)
            .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineId));

    baselineActivityRepository.deleteAll(
        baselineActivityRepository.findByBaselineId(baselineId));
    baselineRelationshipRepository.deleteAll(
        baselineRelationshipRepository.findByBaselineId(baselineId));
    baselineRepository.delete(baseline);
    auditService.logDelete("Baseline", baselineId);
  }

  public List<BaselineVarianceResponse> getVariance(UUID projectId, UUID baselineId) {
    Baseline baseline =
        baselineRepository
            .findById(baselineId)
            .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineId));

    if (!baseline.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Baseline", baselineId);
    }

    List<BaselineActivity> baselineActivities =
        baselineActivityRepository.findByBaselineId(baselineId);

    // Load current activities and cost data
    List<Activity> currentActivities = activityRepository.findByProjectId(projectId);
    Map<UUID, Activity> activityMap = currentActivities.stream()
        .collect(Collectors.toMap(Activity::getId, a -> a));

    List<ActivityExpense> allExpenses = activityExpenseRepository.findByProjectId(projectId);
    Map<UUID, List<ActivityExpense>> expensesByActivity = allExpenses.stream()
        .filter(e -> e.getActivityId() != null)
        .collect(Collectors.groupingBy(ActivityExpense::getActivityId));

    List<ResourceAssignment> allAssignments = resourceAssignmentRepository.findByProjectId(projectId);
    Map<UUID, List<ResourceAssignment>> assignmentsByActivity = allAssignments.stream()
        .collect(Collectors.groupingBy(ResourceAssignment::getActivityId));

    return baselineActivities.stream()
        .map(ba -> calculateVariance(ba, activityMap, expensesByActivity, assignmentsByActivity))
        .toList();
  }

  private BaselineVarianceResponse calculateVariance(
      BaselineActivity baselineActivity,
      Map<UUID, Activity> currentActivityMap,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity) {

    Activity currentActivity = currentActivityMap.get(baselineActivity.getActivityId());
    String activityName = currentActivity != null ? currentActivity.getName() : "Deleted Activity";

    Long startVarianceDays = 0L;
    Long finishVarianceDays = 0L;
    Double durationVariance = 0.0;
    BigDecimal costVariance = BigDecimal.ZERO;

    if (currentActivity != null) {
      // Schedule variance (positive = delayed)
      if (baselineActivity.getEarlyStart() != null && currentActivity.getPlannedStartDate() != null) {
        startVarianceDays = ChronoUnit.DAYS.between(
            baselineActivity.getEarlyStart(),
            currentActivity.getPlannedStartDate());
      }
      if (baselineActivity.getEarlyFinish() != null && currentActivity.getPlannedFinishDate() != null) {
        finishVarianceDays = ChronoUnit.DAYS.between(
            baselineActivity.getEarlyFinish(),
            currentActivity.getPlannedFinishDate());
      }

      // Duration variance
      if (baselineActivity.getOriginalDuration() != null && currentActivity.getOriginalDuration() != null) {
        durationVariance = currentActivity.getOriginalDuration() - baselineActivity.getOriginalDuration();
      }

      // Cost variance = current actual cost - baseline planned cost
      BigDecimal currentActualCost = calculateActualCost(
          currentActivity, expensesByActivity, assignmentsByActivity);
      BigDecimal baselinePlannedCost = baselineActivity.getPlannedCost() != null
          ? baselineActivity.getPlannedCost() : BigDecimal.ZERO;
      costVariance = currentActualCost.subtract(baselinePlannedCost);
    }

    return new BaselineVarianceResponse(
        baselineActivity.getActivityId(),
        activityName,
        startVarianceDays,
        finishVarianceDays,
        durationVariance,
        costVariance);
  }

  public List<ScheduleComparisonResponse> getScheduleComparison(UUID projectId, UUID baselineId) {
    Baseline baseline = baselineRepository
        .findById(baselineId)
        .orElseThrow(() -> new ResourceNotFoundException("Baseline", baselineId));

    if (!baseline.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Baseline", baselineId);
    }

    List<BaselineActivity> baselineActivities = baselineActivityRepository.findByBaselineId(baselineId);
    Map<UUID, BaselineActivity> baselineActivityMap = baselineActivities.stream()
        .collect(Collectors.toMap(BaselineActivity::getActivityId, a -> a));

    List<Activity> currentActivities = activityRepository.findByProjectId(projectId);

    // Build comparison for current activities
    List<ScheduleComparisonResponse> comparisons = currentActivities.stream()
        .map(current -> compareActivity(current, baselineActivityMap.get(current.getId())))
        .collect(Collectors.toList());

    // Add DELETED entries for baseline activities not in current set
    Map<UUID, Activity> currentMap = currentActivities.stream()
        .collect(Collectors.toMap(Activity::getId, a -> a));
    for (BaselineActivity ba : baselineActivities) {
      if (!currentMap.containsKey(ba.getActivityId())) {
        comparisons.add(new ScheduleComparisonResponse(
            ba.getActivityId(),
            "Deleted Activity",
            null,
            ba.getEarlyStart(),
            0L,
            null,
            ba.getEarlyFinish(),
            0L,
            ScheduleComparisonResponse.ComparisonStatus.DELETED));
      }
    }

    return comparisons;
  }

  private ScheduleComparisonResponse compareActivity(Activity current, BaselineActivity baseline) {
    ScheduleComparisonResponse.ComparisonStatus status;
    LocalDate currentStart = current.getPlannedStartDate();
    LocalDate baselineStart = baseline != null ? baseline.getEarlyStart() : null;
    LocalDate currentFinish = current.getPlannedFinishDate();
    LocalDate baselineFinish = baseline != null ? baseline.getEarlyFinish() : null;

    if (baseline == null) {
      status = ScheduleComparisonResponse.ComparisonStatus.ADDED;
    } else if (areDatesEqual(currentStart, baselineStart) && areDatesEqual(currentFinish, baselineFinish)) {
      status = ScheduleComparisonResponse.ComparisonStatus.UNCHANGED;
    } else {
      status = ScheduleComparisonResponse.ComparisonStatus.CHANGED;
    }

    Long startVariance = calculateDaysDifference(baselineStart, currentStart);
    Long finishVariance = calculateDaysDifference(baselineFinish, currentFinish);

    return new ScheduleComparisonResponse(
        current.getId(),
        current.getName(),
        currentStart,
        baselineStart,
        startVariance,
        currentFinish,
        baselineFinish,
        finishVariance,
        status);
  }

  private BigDecimal calculatePlannedCost(
      Activity activity,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity) {
    BigDecimal cost = BigDecimal.ZERO;
    List<ActivityExpense> expenses = expensesByActivity.get(activity.getId());
    if (expenses != null) {
      for (ActivityExpense e : expenses) {
        if (e.getBudgetedCost() != null) cost = cost.add(e.getBudgetedCost());
      }
    }
    List<ResourceAssignment> assignments = assignmentsByActivity.get(activity.getId());
    if (assignments != null) {
      for (ResourceAssignment ra : assignments) {
        if (ra.getPlannedCost() != null) cost = cost.add(ra.getPlannedCost());
      }
    }
    return cost;
  }

  private BigDecimal calculateActualCost(
      Activity activity,
      Map<UUID, List<ActivityExpense>> expensesByActivity,
      Map<UUID, List<ResourceAssignment>> assignmentsByActivity) {
    BigDecimal cost = BigDecimal.ZERO;
    List<ActivityExpense> expenses = expensesByActivity.get(activity.getId());
    if (expenses != null) {
      for (ActivityExpense e : expenses) {
        if (e.getActualCost() != null) cost = cost.add(e.getActualCost());
      }
    }
    List<ResourceAssignment> assignments = assignmentsByActivity.get(activity.getId());
    if (assignments != null) {
      for (ResourceAssignment ra : assignments) {
        if (ra.getActualCost() != null) cost = cost.add(ra.getActualCost());
      }
    }
    return cost;
  }

  private boolean areDatesEqual(LocalDate date1, LocalDate date2) {
    if (date1 == null && date2 == null) return true;
    return date1 != null && date1.equals(date2);
  }

  private Long calculateDaysDifference(LocalDate from, LocalDate to) {
    if (from == null || to == null) return 0L;
    return ChronoUnit.DAYS.between(from, to);
  }
}
