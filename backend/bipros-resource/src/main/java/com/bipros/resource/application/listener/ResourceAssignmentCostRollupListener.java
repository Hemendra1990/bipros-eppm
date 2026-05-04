package com.bipros.resource.application.listener;

import com.bipros.common.event.DailyOutputChangedEvent;
import com.bipros.common.event.ResourceAssignmentActualsRolledUpEvent;
import com.bipros.resource.application.service.ProjectResourceService;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * Recomputes the cost-side rollup on the matching {@code ResourceAssignment} after a daily output
 * row is created or deleted in {@code bipros-project}. The units rollup is done inline by the
 * project module via native SQL — this listener picks the AC story up from there.
 *
 * <p>Runs AFTER_COMMIT in REQUIRES_NEW so {@code actual_units} is already persisted when we read it.
 *
 * <p>Rate selection uses the same two-tier chain as {@code ResourceAssignmentService}:
 * {@code ProjectResource.rateOverride} → {@code Resource.costPerUnit} → null. When no rate is
 * available, cost fields are cleared (left null) rather than silently zeroed, so a missing rate
 * is visible to reviewers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceAssignmentCostRollupListener {

  private final ResourceAssignmentRepository assignmentRepository;
  private final ResourceRepository resourceRepository;
  private final ProjectResourceService projectResourceService;
  private final ApplicationEventPublisher eventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onDailyOutputChanged(DailyOutputChangedEvent event) {
    if (event.resourceId() == null) {
      log.debug("ResourceId is null (role-only assignment) — cost rollup skipped");
      return;
    }
    ResourceAssignment assignment = assignmentRepository
        .findByProjectIdAndActivityIdAndResourceId(
            event.projectId(), event.activityId(), event.resourceId())
        .orElse(null);
    if (assignment == null) {
      log.debug("No ResourceAssignment for project={} activity={} resource={} — cost rollup skipped",
          event.projectId(), event.activityId(), event.resourceId());
      return;
    }

    BigDecimal rate = resolveRate(assignment);
    Double actualUnits = assignment.getActualUnits();
    Double remainingUnits = assignment.getRemainingUnits();

    BigDecimal actualCost = (rate != null && actualUnits != null)
        ? rate.multiply(BigDecimal.valueOf(actualUnits))
        : null;
    BigDecimal remainingCost = (rate != null && remainingUnits != null)
        ? rate.multiply(BigDecimal.valueOf(remainingUnits))
        : null;
    BigDecimal atCompletionCost = (actualCost != null && remainingCost != null)
        ? actualCost.add(remainingCost)
        : actualCost != null ? actualCost : remainingCost;

    assignment.setActualCost(actualCost);
    assignment.setRemainingCost(remainingCost);
    assignment.setAtCompletionCost(atCompletionCost);
    assignmentRepository.save(assignment);

    Double plannedSum = assignmentRepository.sumPlannedUnitsByActivityId(event.activityId());
    Double actualSum = assignmentRepository.sumActualUnitsByActivityId(event.activityId());
    eventPublisher.publishEvent(new ResourceAssignmentActualsRolledUpEvent(
        event.projectId(), event.activityId(), plannedSum, actualSum));

    log.debug("Cost rollup: assignment={} actualUnits={} rate={} -> actualCost={} remainingCost={} eac={}",
        assignment.getId(), actualUnits, rate, actualCost, remainingCost, atCompletionCost);
  }

  private BigDecimal resolveRate(ResourceAssignment assignment) {
    // Two-tier chain: pool override → resource's costPerUnit → null. Same chain
    // ResourceAssignmentService uses for plannedCost so AC and PV stay consistent.
    BigDecimal rateOverride = projectResourceService.resolveRateOverride(
        assignment.getProjectId(), assignment.getResourceId());
    if (rateOverride != null) return rateOverride;

    Resource resource = resourceRepository.findById(assignment.getResourceId()).orElse(null);
    return resource == null ? null : resource.getCostPerUnit();
  }
}
