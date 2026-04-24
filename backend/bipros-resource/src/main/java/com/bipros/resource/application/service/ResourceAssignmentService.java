package com.bipros.resource.application.service;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.resource.application.dto.CreateResourceAssignmentRequest;
import com.bipros.resource.application.dto.ResourceAssignmentResponse;
import com.bipros.resource.application.dto.ResourceUsageEntry;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ResourceAssignmentService {

  private final ResourceAssignmentRepository assignmentRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceRateRepository rateRepository;
  private final ActivityRepository activityRepository;
  private final AuditService auditService;

  /** Batch-hydrate resource + activity names onto a list of assignments in 2 queries total. */
  private List<ResourceAssignmentResponse> hydrate(List<ResourceAssignment> assignments) {
    if (assignments.isEmpty()) return List.of();
    var resourceIds = assignments.stream().map(ResourceAssignment::getResourceId).distinct().toList();
    var activityIds = assignments.stream().map(ResourceAssignment::getActivityId).distinct().toList();
    Map<UUID, String> resourceNames = resourceRepository.findAllById(resourceIds).stream()
        .collect(Collectors.toMap(Resource::getId, Resource::getName));
    Map<UUID, String> activityNames = activityRepository.findAllById(activityIds).stream()
        .collect(Collectors.toMap(Activity::getId, Activity::getName));
    return assignments.stream()
        .map(a -> ResourceAssignmentResponse.from(a,
            resourceNames.get(a.getResourceId()),
            activityNames.get(a.getActivityId())))
        .toList();
  }

  /**
   * Pick the latest rate (by effectiveDate) for the resource+type and multiply by units.
   *
   * <p>Prefers the PMS MasterData {@code budgetedRate} when set (Screen 05) so project-baseline
   * rate locks take effect; falls back to the legacy {@code pricePerUnit} otherwise. Callers
   * that want the current market rate should use {@link #computeActualCost} instead.
   */
  private BigDecimal computePlannedCost(UUID resourceId, String rateType, Double plannedUnits) {
    if (plannedUnits == null || rateType == null) return null;
    List<ResourceRate> rates = rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(
        resourceId, rateType);
    if (rates.isEmpty()) return null;
    ResourceRate latest = rates.get(0);
    BigDecimal baseRate = latest.getBudgetedRate() != null
        ? latest.getBudgetedRate()
        : latest.getPricePerUnit();
    if (baseRate == null) return null;
    return baseRate.multiply(BigDecimal.valueOf(plannedUnits));
  }

  /**
   * Compute incurred cost using the market/actual rate when set (PMS Screen 05), else fall back
   * to the budgeted or legacy price. Used by cost-summary / EVM callers that need AC.
   */
  @SuppressWarnings("unused")
  private BigDecimal computeActualCost(UUID resourceId, String rateType, Double actualUnits) {
    if (actualUnits == null || rateType == null) return null;
    List<ResourceRate> rates = rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(
        resourceId, rateType);
    if (rates.isEmpty()) return null;
    ResourceRate latest = rates.get(0);
    BigDecimal rate = latest.getActualRate() != null
        ? latest.getActualRate()
        : latest.getBudgetedRate() != null
            ? latest.getBudgetedRate()
            : latest.getPricePerUnit();
    if (rate == null) return null;
    return rate.multiply(BigDecimal.valueOf(actualUnits));
  }

  /** Single-assignment hydration path. */
  private ResourceAssignmentResponse hydrate(ResourceAssignment a) {
    String rn = resourceRepository.findById(a.getResourceId()).map(Resource::getName).orElse(null);
    String an = activityRepository.findById(a.getActivityId()).map(Activity::getName).orElse(null);
    return ResourceAssignmentResponse.from(a, rn, an);
  }

  public ResourceAssignmentResponse assignResource(CreateResourceAssignmentRequest request) {
    log.info("Assigning resource: activityId={}, resourceId={}, projectId={}",
        request.activityId(), request.resourceId(), request.projectId());

    if (!resourceRepository.existsById(request.resourceId())) {
      throw new ResourceNotFoundException("Resource", request.resourceId());
    }

    Resource resource = resourceRepository.findById(request.resourceId()).orElseThrow();

    assignmentRepository.findByActivityId(request.activityId())
        .stream()
        .filter(a -> a.getResourceId().equals(request.resourceId()))
        .findFirst()
        .ifPresent(a -> {
          throw new BusinessRuleException(
              "DUPLICATE_ASSIGNMENT",
              "Resource already assigned to this activity: activityId=" + request.activityId() +
              ", resourceId=" + request.resourceId());
        });

    // Default to STANDARD rate when the caller didn't specify one (BUG-031).
    String effectiveRateType = request.rateType() != null ? request.rateType() : "STANDARD";

    // Back-fill planned dates from the activity when not provided (BUG-031).
    LocalDate plannedStart = request.plannedStartDate();
    LocalDate plannedFinish = request.plannedFinishDate();
    if (plannedStart == null || plannedFinish == null) {
      Activity activity = activityRepository.findById(request.activityId()).orElse(null);
      if (activity != null) {
        if (plannedStart == null) plannedStart = activity.getPlannedStartDate();
        if (plannedFinish == null) plannedFinish = activity.getPlannedFinishDate();
      }
    }

    BigDecimal plannedCost = computePlannedCost(
        request.resourceId(), effectiveRateType, request.plannedUnits());

    ResourceAssignment assignment = ResourceAssignment.builder()
        .activityId(request.activityId())
        .resourceId(request.resourceId())
        .roleId(request.roleId())
        .projectId(request.projectId())
        .plannedUnits(request.plannedUnits())
        .rateType(effectiveRateType)
        .resourceCurveId(request.resourceCurveId())
        .plannedStartDate(plannedStart)
        .plannedFinishDate(plannedFinish)
        .plannedCost(plannedCost)
        .build();

    ResourceAssignment saved = assignmentRepository.save(assignment);
    log.info("Resource assignment created: id={}", saved.getId());

    ResourceAssignmentResponse response = hydrate(saved);
    auditService.logCreate("ResourceAssignment", saved.getId(), response);
    return response;
  }

  public ResourceAssignmentResponse getAssignment(UUID id) {
    log.info("Fetching resource assignment: id={}", id);
    ResourceAssignment assignment = assignmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceAssignment", id));
    return hydrate(assignment);
  }

  public List<ResourceAssignmentResponse> getAssignmentsByActivity(UUID activityId) {
    log.info("Fetching assignments for activity: {}", activityId);
    return hydrate(assignmentRepository.findByActivityId(activityId));
  }

  public List<ResourceAssignmentResponse> getAssignmentsByResource(UUID resourceId) {
    log.info("Fetching assignments for resource: {}", resourceId);
    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }
    return hydrate(assignmentRepository.findByResourceId(resourceId));
  }

  public List<ResourceAssignmentResponse> getAssignmentsByProject(UUID projectId) {
    log.info("Fetching assignments for project: {}", projectId);
    return hydrate(assignmentRepository.findByProjectId(projectId));
  }

  public ResourceAssignmentResponse updateAssignment(UUID id, CreateResourceAssignmentRequest request) {
    log.info("Updating resource assignment: id={}", id);
    ResourceAssignment assignment = assignmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceAssignment", id));

    if (!assignment.getResourceId().equals(request.resourceId())) {
      assignmentRepository.findByActivityId(request.activityId())
          .stream()
          .filter(a -> a.getResourceId().equals(request.resourceId()) && !a.getId().equals(id))
          .findFirst()
          .ifPresent(a -> {
            throw new BusinessRuleException(
                "DUPLICATE_ASSIGNMENT",
                "Resource already assigned to this activity: activityId=" + request.activityId() +
                ", resourceId=" + request.resourceId());
          });
    }

    assignment.setActivityId(request.activityId());
    assignment.setResourceId(request.resourceId());
    assignment.setRoleId(request.roleId());
    assignment.setProjectId(request.projectId());
    assignment.setPlannedUnits(request.plannedUnits());
    assignment.setRateType(request.rateType());
    assignment.setResourceCurveId(request.resourceCurveId());
    assignment.setPlannedStartDate(request.plannedStartDate());
    assignment.setPlannedFinishDate(request.plannedFinishDate());

    ResourceAssignment updated = assignmentRepository.save(assignment);
    log.info("Resource assignment updated: id={}", id);

    ResourceAssignmentResponse response = hydrate(updated);
    auditService.logUpdate("ResourceAssignment", id, "assignment", assignment, response);
    return response;
  }

  public void removeAssignment(UUID assignmentId) {
    log.info("Removing resource assignment: id={}", assignmentId);
    if (!assignmentRepository.existsById(assignmentId)) {
      throw new ResourceNotFoundException("ResourceAssignment", assignmentId);
    }
    assignmentRepository.deleteById(assignmentId);
    log.info("Resource assignment removed: id={}", assignmentId);

    // Audit log deletion
    auditService.logDelete("ResourceAssignment", assignmentId);
  }

  public List<ResourceUsageEntry> getResourceUsageProfile(UUID resourceId, LocalDate startDate, LocalDate endDate) {
    log.info("Fetching resource usage profile: resourceId={}, startDate={}, endDate={}",
        resourceId, startDate, endDate);

    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }

    Resource resource = resourceRepository.findById(resourceId).orElseThrow();
    List<ResourceAssignment> assignments =
        assignmentRepository.findByResourceIdAndPlannedStartDateBetween(resourceId, startDate, endDate);

    TreeMap<LocalDate, Double> plannedUsage = new TreeMap<>();
    TreeMap<LocalDate, Double> actualUsage = new TreeMap<>();

    for (ResourceAssignment assignment : assignments) {
      LocalDate assignStart = assignment.getPlannedStartDate();
      LocalDate assignEnd = assignment.getPlannedFinishDate();

      if (assignStart != null && assignEnd != null) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(assignStart, assignEnd) + 1;
        if (days > 0 && assignment.getPlannedUnits() != null) {
          double unitsPerDay = assignment.getPlannedUnits() / days;
          LocalDate current = assignStart;
          while (!current.isAfter(assignEnd)) {
            if (!current.isBefore(startDate) && !current.isAfter(endDate)) {
              plannedUsage.merge(current, unitsPerDay, Double::sum);
            }
            current = current.plusDays(1);
          }
        }
      }

      if (assignment.getActualStartDate() != null && assignment.getActualFinishDate() != null) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(
            assignment.getActualStartDate(), assignment.getActualFinishDate()) + 1;
        if (days > 0 && assignment.getActualUnits() != null) {
          double unitsPerDay = assignment.getActualUnits() / days;
          LocalDate current = assignment.getActualStartDate();
          while (!current.isAfter(assignment.getActualFinishDate())) {
            if (!current.isBefore(startDate) && !current.isAfter(endDate)) {
              actualUsage.merge(current, unitsPerDay, Double::sum);
            }
            current = current.plusDays(1);
          }
        }
      }
    }

    List<ResourceUsageEntry> entries = new ArrayList<>();
    LocalDate current = startDate;
    while (!current.isAfter(endDate)) {
      entries.add(new ResourceUsageEntry(
          resourceId,
          resource.getName(),
          current,
          plannedUsage.getOrDefault(current, 0.0),
          actualUsage.getOrDefault(current, 0.0)
      ));
      current = current.plusDays(1);
    }

    log.info("Usage profile computed: {} entries", entries.size());
    return entries;
  }
}
