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
import com.bipros.resource.domain.model.ResourceRole;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.bipros.resource.domain.repository.ResourceRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ResourceAssignmentService {

  private final ResourceAssignmentRepository assignmentRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceRateRepository rateRepository;
  private final ResourceRoleRepository roleRepository;
  private final ActivityRepository activityRepository;
  private final AuditService auditService;

  /** Batch-hydrate resource + activity + role names onto a list of assignments in 3 queries total. */
  private List<ResourceAssignmentResponse> hydrate(List<ResourceAssignment> assignments) {
    if (assignments.isEmpty()) return List.of();
    var resourceIds = assignments.stream()
        .map(ResourceAssignment::getResourceId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    var roleIds = assignments.stream()
        .map(ResourceAssignment::getRoleId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    var activityIds = assignments.stream().map(ResourceAssignment::getActivityId).distinct().toList();
    Map<UUID, String> resourceNames = resourceIds.isEmpty()
        ? Map.of()
        : resourceRepository.findAllById(resourceIds).stream()
            .collect(Collectors.toMap(Resource::getId, Resource::getName));
    Map<UUID, String> roleNames = roleIds.isEmpty()
        ? Map.of()
        : roleRepository.findAllById(roleIds).stream()
            .collect(Collectors.toMap(ResourceRole::getId, ResourceRole::getName));
    Map<UUID, String> activityNames = activityRepository.findAllById(activityIds).stream()
        .collect(Collectors.toMap(Activity::getId, Activity::getName));
    return assignments.stream()
        .map(a -> ResourceAssignmentResponse.from(a,
            a.getResourceId() == null ? null : resourceNames.get(a.getResourceId()),
            a.getActivityId() == null ? null : activityNames.get(a.getActivityId()),
            a.getRoleId() == null ? null : roleNames.get(a.getRoleId())))
        .toList();
  }

  /**
   * Pick the latest rate (by effectiveDate) for the resource+type and multiply by units.
   * Prefers {@code budgetedRate} when set, falls back to {@code pricePerUnit}.
   */
  private BigDecimal computePlannedCost(UUID resourceId, String rateType, Double plannedUnits) {
    if (plannedUnits == null) return null;
    List<ResourceRate> rates = rateType != null
        ? rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(resourceId, rateType)
        : List.of();
    if (rates.isEmpty()) {
      rates = rateRepository.findByResourceIdOrderByEffectiveDateDesc(resourceId);
    }
    if (rates.isEmpty()) return null;
    ResourceRate latest = rates.get(0);
    BigDecimal baseRate = latest.getBudgetedRate() != null
        ? latest.getBudgetedRate()
        : latest.getPricePerUnit();
    if (baseRate == null) return null;
    return baseRate.multiply(BigDecimal.valueOf(plannedUnits));
  }

  /**
   * Compute planned cost from a role's defaultRate when no specific resource is assigned yet.
   */
  private BigDecimal computePlannedCostFromRole(UUID roleId, String rateType, Double plannedUnits) {
    if (plannedUnits == null) return null;
    ResourceRole role = roleRepository.findById(roleId).orElse(null);
    if (role == null || role.getDefaultRate() == null) return null;
    return role.getDefaultRate().multiply(BigDecimal.valueOf(plannedUnits));
  }

  @SuppressWarnings("unused")
  private BigDecimal computeActualCost(UUID resourceId, String rateType, Double actualUnits) {
    if (actualUnits == null) return null;
    List<ResourceRate> rates = rateType != null
        ? rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(resourceId, rateType)
        : List.of();
    if (rates.isEmpty()) {
      rates = rateRepository.findByResourceIdOrderByEffectiveDateDesc(resourceId);
    }
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

  private ResourceAssignmentResponse hydrate(ResourceAssignment a) {
    String rn = a.getResourceId() != null
        ? resourceRepository.findById(a.getResourceId()).map(Resource::getName).orElse(null)
        : null;
    String an = activityRepository.findById(a.getActivityId()).map(Activity::getName).orElse(null);
    String ron = a.getRoleId() != null
        ? roleRepository.findById(a.getRoleId()).map(ResourceRole::getName).orElse(null)
        : null;
    return ResourceAssignmentResponse.from(a, rn, an, ron);
  }

  public ResourceAssignmentResponse assignResource(CreateResourceAssignmentRequest request) {
    log.info("Assigning resource: activityId={}, resourceId={}, roleId={}, projectId={}",
        request.activityId(), request.resourceId(), request.roleId(), request.projectId());

    if (request.resourceId() == null && request.roleId() == null) {
      throw new BusinessRuleException("ASSIGNMENT_TARGET_REQUIRED",
          "Either roleId or resourceId is required");
    }

    if (request.resourceId() != null && !resourceRepository.existsById(request.resourceId())) {
      throw new ResourceNotFoundException("Resource", request.resourceId());
    }

    if (request.roleId() != null && !roleRepository.existsById(request.roleId())) {
      throw new ResourceNotFoundException("ResourceRole", request.roleId());
    }

    if (request.resourceId() != null) {
      assignmentRepository.findByActivityId(request.activityId())
          .stream()
          .filter(a -> request.resourceId().equals(a.getResourceId()))
          .findFirst()
          .ifPresent(a -> {
            throw new BusinessRuleException(
                "DUPLICATE_ASSIGNMENT",
                "Resource already assigned to this activity: activityId=" + request.activityId() +
                ", resourceId=" + request.resourceId());
          });
    } else {
      assignmentRepository.findByActivityIdAndResourceIdIsNullAndRoleId(
              request.activityId(), request.roleId())
          .ifPresent(a -> {
            throw new BusinessRuleException(
                "DUPLICATE_ROLE_ASSIGNMENT",
                "Role-only slot already exists for this activity: activityId=" + request.activityId() +
                ", roleId=" + request.roleId());
          });
    }

    String effectiveRateType = request.rateType() != null ? request.rateType() : "STANDARD";

    LocalDate plannedStart = request.plannedStartDate();
    LocalDate plannedFinish = request.plannedFinishDate();
    if (plannedStart == null || plannedFinish == null) {
      Activity activity = activityRepository.findById(request.activityId()).orElse(null);
      if (activity != null) {
        if (plannedStart == null) plannedStart = activity.getPlannedStartDate();
        if (plannedFinish == null) plannedFinish = activity.getPlannedFinishDate();
      }
    }

    BigDecimal plannedCost;
    if (request.resourceId() != null) {
      plannedCost = computePlannedCost(
          request.resourceId(), effectiveRateType, request.plannedUnits());
    } else {
      plannedCost = computePlannedCostFromRole(
          request.roleId(), effectiveRateType, request.plannedUnits());
    }

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

  public ResourceAssignmentResponse staffAssignment(UUID assignmentId, UUID resourceId, boolean override) {
    log.info("Staffing assignment: assignmentId={}, resourceId={}, override={}", assignmentId, resourceId, override);

    ResourceAssignment assignment = assignmentRepository.findById(assignmentId)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceAssignment", assignmentId));

    if (assignment.getResourceId() != null) {
      throw new BusinessRuleException("ALREADY_STAFFED",
          "Assignment is already staffed: assignmentId=" + assignmentId);
    }
    if (assignment.getRoleId() == null) {
      throw new BusinessRuleException("NO_ROLE_TO_STAFF",
          "Assignment has no role to staff against: assignmentId=" + assignmentId);
    }

    Resource resource = resourceRepository.findById(resourceId)
        .orElseThrow(() -> new ResourceNotFoundException("Resource", resourceId));

    // Eligibility: resource's role must match assignment role (the new model has 1 role per resource)
    if (resource.getRole() == null || !assignment.getRoleId().equals(resource.getRole().getId())) {
      if (!override) {
        throw new BusinessRuleException("RESOURCE_NOT_QUALIFIED",
            "Resource's role does not match the required assignment role: resourceId=" + resourceId
                + ", roleId=" + assignment.getRoleId());
      }
      var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
      boolean isAdmin = auth != null && auth.getAuthorities().stream()
          .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
      if (!isAdmin) {
        throw new BusinessRuleException("STAFF_OVERRIDE_NOT_AUTHORIZED",
            "Only admins can override qualification checks");
      }
    }

    assignmentRepository.findByActivityId(assignment.getActivityId())
        .stream()
        .filter(a -> resourceId.equals(a.getResourceId()))
        .findFirst()
        .ifPresent(a -> {
          throw new BusinessRuleException(
              "DUPLICATE_ASSIGNMENT",
              "Resource already assigned to this activity: activityId=" + assignment.getActivityId() +
              ", resourceId=" + resourceId);
        });

    assignment.setResourceId(resourceId);
    BigDecimal plannedCost = computePlannedCost(resourceId, assignment.getRateType(), assignment.getPlannedUnits());
    assignment.setPlannedCost(plannedCost);

    ResourceAssignment saved = assignmentRepository.save(assignment);
    log.info("Assignment staffed: id={}, resourceId={}", saved.getId(), resourceId);

    ResourceAssignmentResponse response = hydrate(saved);
    auditService.logUpdate("ResourceAssignment", saved.getId(), "staff", assignment, response);
    return response;
  }

  public ResourceAssignmentResponse swapResource(UUID assignmentId, UUID newResourceId, boolean override) {
    log.info("Swapping resource: assignmentId={}, newResourceId={}, override={}", assignmentId, newResourceId, override);

    ResourceAssignment assignment = assignmentRepository.findById(assignmentId)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceAssignment", assignmentId));

    if (assignment.getResourceId() == null) {
      throw new BusinessRuleException("NOT_STAFFED",
          "Assignment is not staffed yet; use staff endpoint instead: assignmentId=" + assignmentId);
    }
    if (assignment.getRoleId() == null) {
      throw new BusinessRuleException("NO_ROLE",
          "Assignment has no role; direct resource swap is not supported: assignmentId=" + assignmentId);
    }

    Resource resource = resourceRepository.findById(newResourceId)
        .orElseThrow(() -> new ResourceNotFoundException("Resource", newResourceId));

    if (resource.getRole() == null || !assignment.getRoleId().equals(resource.getRole().getId())) {
      if (!override) {
        throw new BusinessRuleException("RESOURCE_NOT_QUALIFIED",
            "Resource's role does not match the required assignment role: resourceId=" + newResourceId
                + ", roleId=" + assignment.getRoleId());
      }
      var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
      boolean isAdmin = auth != null && auth.getAuthorities().stream()
          .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
      if (!isAdmin) {
        throw new BusinessRuleException("SWAP_OVERRIDE_NOT_AUTHORIZED",
            "Only admins can override qualification checks");
      }
    }

    assignmentRepository.findByActivityId(assignment.getActivityId())
        .stream()
        .filter(a -> newResourceId.equals(a.getResourceId()) && !a.getId().equals(assignmentId))
        .findFirst()
        .ifPresent(a -> {
          throw new BusinessRuleException(
              "DUPLICATE_ASSIGNMENT",
              "Resource already assigned to this activity: activityId=" + assignment.getActivityId() +
              ", resourceId=" + newResourceId);
        });

    assignment.setResourceId(newResourceId);
    BigDecimal plannedCost = computePlannedCost(newResourceId, assignment.getRateType(), assignment.getPlannedUnits());
    assignment.setPlannedCost(plannedCost);

    ResourceAssignment saved = assignmentRepository.save(assignment);
    log.info("Resource swapped: id={}, newResourceId={}", saved.getId(), newResourceId);

    ResourceAssignmentResponse response = hydrate(saved);
    auditService.logUpdate("ResourceAssignment", saved.getId(), "swap", assignment, response);
    return response;
  }

  public ResourceAssignmentResponse getAssignment(UUID id) {
    ResourceAssignment assignment = assignmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceAssignment", id));
    return hydrate(assignment);
  }

  public List<ResourceAssignmentResponse> getAssignmentsByActivity(UUID activityId) {
    return hydrate(assignmentRepository.findByActivityId(activityId));
  }

  public List<ResourceAssignmentResponse> getAssignmentsByResource(UUID resourceId) {
    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }
    return hydrate(assignmentRepository.findByResourceId(resourceId));
  }

  public List<ResourceAssignmentResponse> getAssignmentsByProject(UUID projectId) {
    return hydrate(assignmentRepository.findByProjectId(projectId));
  }

  public ResourceAssignmentResponse updateAssignment(UUID id, CreateResourceAssignmentRequest request) {
    ResourceAssignment assignment = assignmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceAssignment", id));

    boolean wasStaffed = assignment.getResourceId() != null;
    boolean willBeStaffed = request.resourceId() != null;
    if (wasStaffed != willBeStaffed) {
      throw new BusinessRuleException("STAFFED_STATE_CHANGE_NOT_ALLOWED",
          "Use /staff or /swap endpoints to change the staffed state of an assignment");
    }

    if (request.resourceId() == null && request.roleId() == null) {
      throw new BusinessRuleException("ASSIGNMENT_TARGET_REQUIRED",
          "Either roleId or resourceId is required");
    }

    if (request.resourceId() != null
        && !request.resourceId().equals(assignment.getResourceId())) {
      assignmentRepository.findByActivityId(request.activityId())
          .stream()
          .filter(a -> request.resourceId().equals(a.getResourceId()) && !a.getId().equals(id))
          .findFirst()
          .ifPresent(a -> {
            throw new BusinessRuleException(
                "DUPLICATE_ASSIGNMENT",
                "Resource already assigned to this activity: activityId=" + request.activityId() +
                ", resourceId=" + request.resourceId());
          });
    }
    if (request.resourceId() == null
        && request.roleId() != null
        && !request.roleId().equals(assignment.getRoleId())) {
      assignmentRepository.findByActivityIdAndResourceIdIsNullAndRoleId(
              request.activityId(), request.roleId())
          .ifPresent(a -> {
            throw new BusinessRuleException(
                "DUPLICATE_ROLE_ASSIGNMENT",
                "Role-only slot already exists for this activity: activityId=" + request.activityId() +
                ", roleId=" + request.roleId());
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

    BigDecimal plannedCost;
    if (request.resourceId() != null) {
      plannedCost = computePlannedCost(
          request.resourceId(), request.rateType(), request.plannedUnits());
    } else {
      plannedCost = computePlannedCostFromRole(
          request.roleId(), request.rateType(), request.plannedUnits());
    }
    assignment.setPlannedCost(plannedCost);

    ResourceAssignment updated = assignmentRepository.save(assignment);
    log.info("Resource assignment updated: id={}", id);

    ResourceAssignmentResponse response = hydrate(updated);
    auditService.logUpdate("ResourceAssignment", id, "assignment", assignment, response);
    return response;
  }

  public int recomputeProjectCosts(UUID projectId) {
    log.info("Recomputing costs for project: projectId={}", projectId);
    List<ResourceAssignment> assignments = assignmentRepository.findByProjectId(projectId);
    int updated = 0;
    for (ResourceAssignment a : assignments) {
      BigDecimal newPlanned = a.getResourceId() != null
          ? computePlannedCost(a.getResourceId(), a.getRateType(), a.getPlannedUnits())
          : computePlannedCostFromRole(a.getRoleId(), a.getRateType(), a.getPlannedUnits());
      BigDecimal actualRate = a.getResourceId() != null
          ? resolveActualRate(a.getResourceId(), a.getRateType())
          : null;
      BigDecimal newActual = (actualRate != null && a.getActualUnits() != null)
          ? actualRate.multiply(BigDecimal.valueOf(a.getActualUnits()))
          : null;
      BigDecimal newRemaining = (actualRate != null && a.getRemainingUnits() != null)
          ? actualRate.multiply(BigDecimal.valueOf(a.getRemainingUnits()))
          : null;
      BigDecimal newEac = (newActual != null && newRemaining != null)
          ? newActual.add(newRemaining)
          : newActual != null ? newActual : newRemaining;

      boolean changed = !Objects.equals(newPlanned, a.getPlannedCost())
          || !Objects.equals(newActual, a.getActualCost())
          || !Objects.equals(newRemaining, a.getRemainingCost())
          || !Objects.equals(newEac, a.getAtCompletionCost());
      if (changed) {
        a.setPlannedCost(newPlanned);
        a.setActualCost(newActual);
        a.setRemainingCost(newRemaining);
        a.setAtCompletionCost(newEac);
        assignmentRepository.save(a);
        updated++;
      }
    }
    log.info("Project cost recompute complete: projectId={}, updated={}", projectId, updated);
    return updated;
  }

  private BigDecimal resolveActualRate(UUID resourceId, String rateType) {
    List<ResourceRate> rates = rateType != null
        ? rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(resourceId, rateType)
        : List.of();
    if (rates.isEmpty()) {
      rates = rateRepository.findByResourceIdOrderByEffectiveDateDesc(resourceId);
    }
    if (rates.isEmpty()) return null;
    ResourceRate latest = rates.get(0);
    if (latest.getActualRate() != null) return latest.getActualRate();
    if (latest.getBudgetedRate() != null) return latest.getBudgetedRate();
    return latest.getPricePerUnit();
  }

  public void removeAssignment(UUID assignmentId) {
    if (!assignmentRepository.existsById(assignmentId)) {
      throw new ResourceNotFoundException("ResourceAssignment", assignmentId);
    }
    assignmentRepository.deleteById(assignmentId);
    auditService.logDelete("ResourceAssignment", assignmentId);
  }

  public List<ResourceUsageEntry> getResourceUsageProfile(UUID resourceId, LocalDate startDate, LocalDate endDate) {
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

    return entries;
  }
}
