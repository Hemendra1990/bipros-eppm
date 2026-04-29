package com.bipros.resource.application.listener;

import com.bipros.common.event.DailyOutputChangedEvent;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.model.ResourceRate;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recomputes the cost-side rollup on the matching {@code ResourceAssignment} after a daily output
 * row is created or deleted in {@code bipros-project}. The units rollup is done inline by the
 * project module via native SQL — this listener picks the AC story up from there.
 *
 * <p>Runs AFTER_COMMIT in REQUIRES_NEW so {@code actual_units} is already persisted when we read it.
 *
 * <p>Rate selection mirrors {@code ResourceAssignmentService#computeActualCost}:
 * latest effective {@code actualRate} → {@code budgetedRate} → {@code pricePerUnit}. When no rate
 * exists for the assignment's {@code rateType}, cost fields are cleared (left null) rather than
 * silently zeroed, so a missing rate is visible to reviewers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceAssignmentCostRollupListener {

  private final ResourceAssignmentRepository assignmentRepository;
  private final ResourceRateRepository rateRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onDailyOutputChanged(DailyOutputChangedEvent event) {
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

    log.debug("Cost rollup: assignment={} actualUnits={} rate={} -> actualCost={} remainingCost={} eac={}",
        assignment.getId(), actualUnits, rate, actualCost, remainingCost, atCompletionCost);
  }

  private BigDecimal resolveRate(ResourceAssignment assignment) {
    if (assignment.getRateType() == null) return null;
    List<ResourceRate> rates = rateRepository.findByResourceIdAndRateTypeOrderByEffectiveDateDesc(
        assignment.getResourceId(), assignment.getRateType());
    if (rates.isEmpty()) return null;
    ResourceRate latest = rates.get(0);
    if (latest.getActualRate() != null) return latest.getActualRate();
    if (latest.getBudgetedRate() != null) return latest.getBudgetedRate();
    return latest.getPricePerUnit();
  }
}
