package com.bipros.resource.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
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
import java.util.TreeMap;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ResourceAssignmentService {

  private final ResourceAssignmentRepository assignmentRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceRateRepository rateRepository;

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

    BigDecimal plannedCost = null;
    if (request.plannedUnits() != null && request.rateType() != null) {
      List<ResourceRate> rates = rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(
          request.resourceId(), request.rateType());
      if (!rates.isEmpty()) {
        ResourceRate latestRate = rates.get(0);
        plannedCost = latestRate.getPricePerUnit().multiply(BigDecimal.valueOf(request.plannedUnits()));
      }
    }

    ResourceAssignment assignment = ResourceAssignment.builder()
        .activityId(request.activityId())
        .resourceId(request.resourceId())
        .roleId(request.roleId())
        .projectId(request.projectId())
        .plannedUnits(request.plannedUnits())
        .rateType(request.rateType())
        .resourceCurveId(request.resourceCurveId())
        .plannedStartDate(request.plannedStartDate())
        .plannedFinishDate(request.plannedFinishDate())
        .plannedCost(plannedCost)
        .build();

    ResourceAssignment saved = assignmentRepository.save(assignment);
    log.info("Resource assignment created: id={}", saved.getId());
    return ResourceAssignmentResponse.from(saved);
  }

  public ResourceAssignmentResponse getAssignment(UUID id) {
    log.info("Fetching resource assignment: id={}", id);
    ResourceAssignment assignment = assignmentRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ResourceAssignment", id));
    return ResourceAssignmentResponse.from(assignment);
  }

  public List<ResourceAssignmentResponse> getAssignmentsByActivity(UUID activityId) {
    log.info("Fetching assignments for activity: {}", activityId);
    return assignmentRepository.findByActivityId(activityId).stream()
        .map(ResourceAssignmentResponse::from)
        .toList();
  }

  public List<ResourceAssignmentResponse> getAssignmentsByResource(UUID resourceId) {
    log.info("Fetching assignments for resource: {}", resourceId);
    if (!resourceRepository.existsById(resourceId)) {
      throw new ResourceNotFoundException("Resource", resourceId);
    }
    return assignmentRepository.findByResourceId(resourceId).stream()
        .map(ResourceAssignmentResponse::from)
        .toList();
  }

  public List<ResourceAssignmentResponse> getAssignmentsByProject(UUID projectId) {
    log.info("Fetching assignments for project: {}", projectId);
    return assignmentRepository.findByProjectId(projectId).stream()
        .map(ResourceAssignmentResponse::from)
        .toList();
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
    return ResourceAssignmentResponse.from(updated);
  }

  public void removeAssignment(UUID assignmentId) {
    log.info("Removing resource assignment: id={}", assignmentId);
    if (!assignmentRepository.existsById(assignmentId)) {
      throw new ResourceNotFoundException("ResourceAssignment", assignmentId);
    }
    assignmentRepository.deleteById(assignmentId);
    log.info("Resource assignment removed: id={}", assignmentId);
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
